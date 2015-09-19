package co.com.sura.distribucion.asignacion.node.commons.aggregate

import akka.persistence._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.concurrent.duration._

import co.com.sura.distribucion.asignacion.node.commons.constants.{ Constants, CoreConstants, LogConstants }
import co.com.sura.distribucion.asignacion.node.commons.exceptions.RecoveryStateException
import co.com.sura.distribucion.asignacion.node.commons.aggregate.ActorWithPersistence.ActorWithPersistenceState
import co.com.sura.distribucion.asignacion.node.commons.aggregate.PersistenceLayer.{ Delete, EventPersisted, SaveState }

/**
 * Define los mensajes específicos procesados en última instancia por el actor.
 */
object PersistenceLayer {

  case class SaveState(state: ActorWithPersistenceState) extends Command

  case object Delete extends Command

  case class EventPersisted(event: Event, remainingNumOfRetries: Int, retryNum: Int) extends Event

}

/**
 * Actor encargado de persistir mensajes de akka-persistence de forma iterativa hasta que los persista todos.
 *
 * @author Andres Ricardo Peralta Perea.
 */
class PersistenceLayer extends PersistentActor with LazyLogging {

  private[PersistenceLayer] val eventsRecovered = mutable.ListBuffer.empty[Event]
  private[PersistenceLayer] val remainingNumOfRetries = Constants.config.getInt("sura.max-number-retries-persist") + 1

  private[PersistenceLayer] var stateOpt = Option.empty[SnapshotOffer]

  /**
   * Identificador del actor para akka-persistence.
   */
  def persistenceId: String = self.path.name.replace(CoreConstants.PERSISTENCE_LAYER_SUFFIX, "")

  /**
   * Maneja la recuperación del estado del actor.
   */
  def receiveRecover: Receive = {
    case fail: RecoveryFailure => throw RecoveryStateException(LogConstants.PERSISTENCE_LAYER_RECOVERY_ERROR.format(persistenceId), fail.cause)

    case RecoveryCompleted =>
      logger.debug(LogConstants.PERSISTENCE_LAYER_RECOVERY_SUCCESS.format(persistenceId))
      // Primero se envía siempre el estado recuperado.
      stateOpt.foreach(context.parent ! _)
      eventsRecovered.foreach(context.parent ! _)
      context.parent ! RecoveryCompleted

    case snap: SnapshotOffer => stateOpt = Option(snap)
    case EventPersisted(event, _, _) => eventsRecovered += event
    case msg => logger.warn(LogConstants.PERSISTENCE_LAYER_RECOVERY_UNKNOWN.format(persistenceId, msg.toString))
  }

  /**
   * Maneja los eventos recibidos por el actor.
   */
  def receiveCommand: Receive = {
    case Delete => deleteMessages(lastSequenceNr)

    case SaveState(state) =>
      context.become(takingSnapshot)
      saveSnapshot(state)

    case event: Event =>
      context.become(persisting)
      persistEvent(EventPersisted(event, remainingNumOfRetries, 0))

    case msg => warnUnknownMsg(msg)
  }

  /**
   * Maneja los eventos del intento de persistencia.
   */
  private def persisting: Receive = {
    case fail: PersistenceFailure =>
      val notPersisted = fail.payload.asInstanceOf[EventPersisted]
      logger.error(LogConstants.PERSISTENCE_LAYER_PERSIST_ERROR.format(persistenceId, notPersisted.event.toString), fail.cause)
      val toPersist = notPersisted.copy(remainingNumOfRetries = notPersisted.remainingNumOfRetries - 1, retryNum = notPersisted.retryNum + 1)
      self ! toPersist // Se debe enviar de nuevo para que akka-persistence haga manejo del contexto.

    case eventPersisted: EventPersisted => retryPersist(eventPersisted)
    case msg => stash()
  }

  /**
   * Maneja los eventos del intento de persistencia.
   */
  private def takingSnapshot: Receive = {
    case fail: SaveSnapshotFailure =>
      logger.error(LogConstants.PERSISTENCE_LAYER_PERSIST_STATE_ERROR.format(persistenceId), fail.cause)
      resetReceive()

    case ok: SaveSnapshotSuccess =>
      logger.debug(LogConstants.PERSISTENCE_LAYER_PERSIST_STATE_SUCCESS.format(persistenceId))
      resetReceive()

    case msg => stash()
  }

  /**
   * Persiste el evento actual.
   * Si tiene éxito indica que ya se puede trabajar el siguiente mensaje y vuelve a re-dirigir los mensajes a la mailbox
   * del actor.
   */
  private def persistEvent(eventPersisted: EventPersisted) = {
    logger.debug(LogConstants.PERSISTENCE_LAYER_PERSIST_EVENT.format(persistenceId, eventPersisted.event.toString))
    persist(eventPersisted) { e =>
      resetReceive()
      logger.debug(LogConstants.PERSISTENCE_LAYER_PERSIST_SUCCESS.format(persistenceId, e.event.toString))
    }
  }

  /**
   * Intenta exponencialmente la persistencia de un evento de akka-persistence del actor.
   */
  private def retryPersist(eventPersisted: EventPersisted) =
    if (eventPersisted.remainingNumOfRetries > 0) {
      val timeRetry = scala.math.exp(eventPersisted.retryNum).round
      val msg = LogConstants.PERSISTENCE_LAYER_PERSIST_EVENT_RETRY.format(
        eventPersisted.retryNum, timeRetry, eventPersisted.remainingNumOfRetries, eventPersisted.event.toString
      )
      logger.info(msg)
      Thread sleep timeRetry.seconds.toMillis
      // wait
      persistEvent(eventPersisted)
    } else {
      logger.error(LogConstants.PERSISTENCE_LAYER_PERSIST_EVENT_ERROR.format(persistenceId, eventPersisted.event.toString))
      context.stop(self)
    }

  private def resetReceive() = {
    context.become(receiveCommand)
    unstashAll()
  }

  private def warnUnknownMsg(msg: Any) = logger.warn(LogConstants.PERSISTENCE_LAYER_UNKNOWN_MSG.format(persistenceId, msg))
}
