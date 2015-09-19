package co.com.sura.distribucion.asignacion.node.commons.aggregate

import akka.actor.SupervisorStrategy._
import akka.actor._
import akka.persistence.{ RecoveryCompleted, SnapshotOffer }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import co.com.sura.distribucion.asignacion.node.commons.constants.{ Constants, CoreConstants, LogConstants }
import co.com.sura.distribucion.asignacion.node.commons.exceptions.RecoveryStateException
import co.com.sura.distribucion.asignacion.node.commons.aggregate.ActorWithPersistence._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.PersistenceLayer.{ Delete, SaveState }

/**
 * Define los mensajes específicos procesados en última instancia por un actor con persistencia.
 */
object ActorWithPersistence {

  var hasPersistenceToken = false

  trait ActorWithPersistenceState

  case object SaveActorState

}

/**
 * Define funciones comunes entre los actores que usan akka-persistence
 *
 * @author Andres Ricardo Peralta Perea.
 */
trait ActorWithPersistence[S <: ActorWithPersistenceState] extends CommonActor with Stash {

  implicit val _: ExecutionContext = context.system.dispatcher

  /**
   * Estado del actor.
   */
  protected var state: S

  private[ActorWithPersistence] val actorLogName = self.path.toStringWithoutAddress
  private[ActorWithPersistence] var hasPersistenceLayer = true
  private[ActorWithPersistence] var remainingNumOfRetries = Constants.config.getInt("sura.max-number-retries-restore")
  private[ActorWithPersistence] var retryNum = 1

  /**
   * Procesa los errores producidos por el actor persistente asociado al actor.
   * Si el error es [[RecoveryStateException]] se intenta recuperar el estado del actor persistente o se detiene si no
   * es posible.
   * Si el error es [[Exception]] se detiene el actor persistente.
   */
  override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy(loggingEnabled = false) {
    case e: RecoveryStateException => retryRecovery(e)
    case e: IllegalStateException =>
      logger.error(LogConstants.PERSISTENT_ACTOR_STASH_FULL.format(actorLogName), e)
      removePersistenceLayer()
      Resume

    case e: StashOverflowException =>
      logger.error(LogConstants.PERSISTENT_ACTOR_STASH_FULL.format(actorLogName), e)
      removePersistenceLayer()
      Resume

    case e: Exception =>
      logger.error(LogConstants.PERSISTENT_ACTOR_ERROR.format(actorLogName), e)
      removePersistenceLayer()
      Stop
  }

  /**
   * Override para crear el actor persistente asociado al actor.
   * Define como manejador de mensajes entrantes la función [[receiveRecoverState]]
   */
  override def preStart(): Unit = {
    context.actorOf(Props[PersistenceLayer], self.path.name.concat(CoreConstants.PERSISTENCE_LAYER_SUFFIX))
    context.become(receiveRecoverState)
    context.system.eventStream.subscribe(self, classOf[DeadLetter])

    val duration = Duration(Constants.config.getString("sura.take-snapshot"))
    val finiteDuration = FiniteDuration(duration.length, duration.unit)
    context.system.scheduler.schedule(finiteDuration, finiteDuration, self, SaveActorState)
  }

  /**
   * Handler de eventos que se persistieron exitosamente y se han recuperado.
   *
   * @param event Evento persistido recuperado.
   */
  protected def handleRecoveredEvent(event: Event): Unit

  /**
   * Función llamada una vez se haya completado la recuperación del actor de forma exitosa.
   */
  protected def recoveryCompleted(): Unit

  /**
   * Envía el evento dado al actor persistente asociado al actor para que éste lo persista.
   * Llama a [[handlePersistedEvent]] una vez se haya enviado a persistir.
   *
   * @param event Evento a persistir.
   */
  protected def persistEvent(event: Event): Unit = {
    sendToPersistenceLayer(event)
    handlePersistedEvent(event)
  }

  /**
   * Handler de eventos que se acaban de enviar a persistir.
   *
   * @param event Evento enviado a persistir.
   */
  protected def handlePersistedEvent(event: Event): Unit

  /**
   * Elimina éste actor y el actor persistente asociado a éste de forma completa y permanente.
   */
  protected def delete(): Unit = {
    sendToPersistenceLayer(Delete)
    selfDelete()
  }

  /**
   * Elimina éste actor.
   */
  protected def selfDelete(): Unit = context.stop(self)

  /**
   * Toma de snapshot del estado del actor.
   */
  protected def trySaveState(state: S): Unit = sendToPersistenceLayer(SaveState(state))

  /**
   * Revisa si la [[DeadLetter]] fue generada por el actor persistente asociado al actor al llenarse su mailbox.
   */
  protected def reviewDeadLetter(deadLetter: DeadLetter): Unit = {
    if (hasPersistenceLayer && context.children.headOption.nonEmpty) {
      if (deadLetter.recipient.path.equals(context.children.head.path)) removePersistenceLayer()
    }
  }

  /**
   * Imprime en modo un WARNING sobre la llegada de un mensaje desconocido mientras se recuperaban los eventos del actor.
   */
  protected def printUnknownRecovery(event: Any): Unit = {
    logger.warn(LogConstants.PERSISTENT_ACTOR_RECOVERY_UNKNOWN.format(actorLogName, event.toString))
  }

  /**
   * Imprime en modo un WARNING sobre la llegada de un mensaje desconocido que fue persistido por el actor.
   */
  protected def printUnknownPersisted(event: Any): Unit = {
    logger.warn(LogConstants.PERSISTENT_ACTOR_PERSIST_UNKNOWN.format(actorLogName, event.toString))
  }

  protected def printState(): Unit = printState(state)

  /**
   * Maneja los mensajes relacionados con la recuperación del estado del actor.
   */
  private def receiveRecoverState: Receive = {
    case SnapshotOffer(_, snapshot: ActorWithPersistenceState) => recoverState(snapshot)
    case event: Event => handleRecoveredEvent(event)

    case RecoveryCompleted =>
      recoveryCompleted()
      context.become(receive)
      unstashAll()

    case msg => stash()
  }

  /**
   * Recupera el estado del actor persistente.
   */
  private def recoverState(recoveredState: ActorWithPersistenceState) = state = recoveredState.asInstanceOf[S]

  /**
   * Reinicia el estado del actor.
   * Intenta exponencialmente la recuperación de estado del actor persistente asociado al actor.
   *
   * @param e Error que provoco fallará la recuperación del estado.
   * @return [[Restart]] si aún hay intentos disponibles.
   *         [[Stop]] si se alcanzó el máximo número de intentos.
   */
  private def retryRecovery(e: RecoveryStateException): Directive = {
    if (remainingNumOfRetries > 0) {
      val timeRetry = scala.math.exp(retryNum).round
      logger.error(LogConstants.PERSISTENT_ACTOR_RECOVERY_RETRY.format(timeRetry, remainingNumOfRetries), e)
      Thread sleep timeRetry.seconds.toMillis
      tryRecovery()
    } else {
      Escalate
    }
  }

  /**
   * Reinicia el actor para que se recupere.
   */
  private def tryRecovery() = {
    remainingNumOfRetries -= 1
    retryNum += 1
    context.become(receiveRecoverState)
    Restart
  }

  /**
   * Envía el mensaje dado al actor persistente asociado a este actor.
   */
  private def sendToPersistenceLayer(msg: Any) = {
    if (hasPersistenceLayer && context.children.headOption.nonEmpty) {
      if (hasPersistenceToken) {
        context.children.head ! msg
      } else {
        logger.warn(LogConstants.PERSISTENT_ACTOR_NOT_PERSISTENCE_TOKEN.format(actorLogName, msg))
      }
    } else {
      logger.warn(LogConstants.PERSISTENT_ACTOR_NOT_PERSISTENCE_LAYER.format(actorLogName, msg))
    }
  }

  /**
   * Define que se trabajara sin capa de persistencia.
   */
  private def removePersistenceLayer() = hasPersistenceLayer = false
}
