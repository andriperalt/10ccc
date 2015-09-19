package co.com.sura.distribucion.asignacion.node.workers.aggregate

import akka.actor.SupervisorStrategy._
import akka.actor._

import scala.util.Try

import co.com.sura.distribucion.asignacion.node.commons.constants.{ ErrorsConstants, CoreConstants, LogConstants }
import co.com.sura.distribucion.asignacion.node.commons.exceptions.RecoveryStateException
import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.core.SystemActors
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ Command, GathererBuilt, CommonActor }
import co.com.sura.distribucion.asignacion.node.gatherer.aggregate.GathererBuilder.BuildGatherer
import co.com.sura.distribucion.asignacion.node.offices.aggregate.OfficesSupervisor.NumberWorkersTransferWorker
import co.com.sura.distribucion.asignacion.node.workers.aggregate.Worker._
import co.com.sura.distribucion.asignacion.node.workers.aggregate.WorkersSupervisor._

/**
 * Define los mensajes específicos procesados en última instancia por el supervisor de los trabajadores.
 */
object WorkersSupervisor {

  /**
   * Comando que inicia la creación de un trabajador recuperado y recupera su estado.
   */
  case class CreateWorkerRecovered(officeCode: String, workerID: String, workerRole: String) extends Command

  /**
   * Comando que inicia la creación de un trabajador en el supervisor.
   */
  case class CreateSupervisedWorker(officeCode: String, workerID: String, workerRole: String, roleMaxAssignments: Int) extends Command

  /**
   * Comando que inicia la recolección del número de trabajadores del supervisor para transferir un trabajador.
   *
   * @param sender Actor del servicio al que se le responderá eventualmente.
   * @param request Solicitud que inicia la recolección.
   */
  case class GiveNumberWorkersTransferWorker(sender: ActorRef, request: TransferWorkerRQ) extends Command

}

/**
 * Supervisor de los actores de las oficinas.
 */
class WorkersSupervisor extends CommonActor {

  /**
   * Procesa los errores producidos por los trabajadores.
   */
  override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy(loggingEnabled = false) {
    case e: RecoveryStateException =>
      logger.error(LogConstants.SYSTEM_NOT_RECOVERED.concat(e.message), e)
      Stop
  }

  // scalastyle:off cyclomatic.complexity
  /**
   * Maneja los mensajes recibidos por el supervisor.
   */
  def receive: Receive = {
    case msg: CreateWorkerRecovered => process(msg)
    case msg: CreateSupervisedWorker => process(sender(), msg)
    case msg: UpdateMaxAssignments => sendToWorkers(msg)
    case msg: DisableWorkerRQ => requestGatherer(sender(), msg)
    case msg: EnableWorkerRQ => requestGatherer(sender(), msg)
    case msg: DeleteWorkerRQ => requestGatherer(sender(), msg)
    case GiveNumberWorkersTransferWorker(originalSender, request) => sender() ! NumberWorkersTransferWorker(originalSender, request, numWorkers)
    case msg: CheckTransfer => sendToWorkers(msg)
    case msg: TransferAssignmentsRQ => requestGatherer(sender(), msg)
    case msg: UnassignWorkerRQ => requestGatherer(sender(), msg)
    case response: GathererBuilt => sendToWorkers(messageByGathererBuilt(response))
    case msg: ChangeWorkerRoleRQ => requestGatherer(sender(), msg)
    case msg => printUnknownMsg(msg)
  }
  // scalastyle:on cyclomatic.complexity

  /**
   * Procesa el mensaje [[CreateWorkerRecovered]].
   */
  private def process(msg: CreateWorkerRecovered) = {
    val nameActor = CoreConstants.WORKER_PREFIX.concat(msg.workerID)
    context.actorOf(Worker.props(msg.workerID), nameActor)
  }

  /**
   * Procesa el mensaje [[CreateSupervisedWorker]].
   */
  private def process(gatherer: ActorRef, msg: CreateSupervisedWorker) = {
    val nameActor = CoreConstants.WORKER_PREFIX.concat(msg.workerID)
    Try {
      val actor = context.actorOf(Worker.props(msg.workerID), nameActor)
      actor.tell(DefineWorkerInfo(msg.officeCode, msg.roleMaxAssignments, msg.workerRole), gatherer)
    }.recover {
      case e: Throwable =>
        val optMsg = Option(e.getMessage)
        val exist = for {
          msg <- optMsg if e.isInstanceOf[InvalidActorNameException] && msg.equals("actor name [" + nameActor + "] is not unique!")
        } yield WorkerExists
        val rsp = exist.getOrElse(CreateWorkerUnsuccessful(ErrorsConstants.UNEXPECTED_ERROR.concat(optMsg.getOrElse(e.toString))))
        gatherer ! rsp
    }.get
  }

  /**
   * Solicita una instancia del gatherer que atienen la solicitud dada.
   */
  private def requestGatherer(sender: ActorRef, request: Request) = {
    context.actorSelection(SystemActors.gathererBuilderPath) ! BuildGatherer(sender, numWorkers, request)
  }

  /**
   * Retorna el número de trabajadores que supervisa éste.
   */
  private def numWorkers = context.children.size

  /**
   * Retorna el mensaje con el gatherer que debe ser enviado a los trabajadores dada la solicitud original que se busca
   * atender.
   */
  private def messageByGathererBuilt(built: GathererBuilt) = built.request match {
    case DisableWorkerRQ(login, selfEnablingDate) => Disable(login, selfEnablingDate, built.gatherer)
    case EnableWorkerRQ(login) => Enable(login, built.gatherer)
    case DeleteWorkerRQ(login) => TryDelete(login, built.gatherer)
    case TransferAssignmentsRQ(loginOrigen, loginDestino, numeroSolicitudes) =>
      CheckTransferAssignments(loginOrigen, loginDestino, numeroSolicitudes, built.gatherer)
    case UnassignWorkerRQ(login, numeroSolicitudes) => TryUnassign(login, numeroSolicitudes, built.gatherer)
    case ChangeWorkerRoleRQ(login, role) => CheckWorkerExist(login, built.gatherer)
    case _ => None // Solo para que sea exhaustivo
  }

  /**
   * Envía el mensaje dado a los trabajadores que supervisa éste.
   *
   * @param msg Mensaje que se enviará.
   */
  private def sendToWorkers(msg: Any) = context.children.foreach(_ ! msg)
}
