package co.com.sura.distribucion.asignacion.node.offices.aggregate

import akka.actor.SupervisorStrategy._
import akka.actor._

import co.com.sura.distribucion.asignacion.node.commons.constants.{ CoreConstants, LogConstants }
import co.com.sura.distribucion.asignacion.node.commons.exceptions.RecoveryStateException
import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.core.SystemActors
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ Command, GathererBuilt, CommonActor }
import co.com.sura.distribucion.asignacion.node.gatherer.aggregate.GathererBuilder.BuildGatherer
import co.com.sura.distribucion.asignacion.node.offices.aggregate.Office.{ CheckOfficeExists, GiveOfficeInfo, GiveWorkersWithCapacity }
import co.com.sura.distribucion.asignacion.node.offices.aggregate.OfficesSupervisor.{ CreateOffice, NumberWorkersTransferWorker }
import co.com.sura.distribucion.asignacion.node.workers.aggregate.Worker.CheckTransfer
import co.com.sura.distribucion.asignacion.node.workers.aggregate.WorkersSupervisor.GiveNumberWorkersTransferWorker

/**
 * Define los mensajes específicos procesados en última instancia por el supervisor de los oficinas.
 */
object OfficesSupervisor {

  /**
   * Comando que inicia la de creación de una oficina en el supervisor.
   *
   * @param regionalCode Código de la regional a la que pertenece la oficina.
   * @param name Nombre de la oficina.
   * @param code Código de la oficina.
   */
  case class CreateOffice(regionalCode: String, name: String, code: String) extends Command

  /**
   * Respuesta a una solicitud de recolección del número de trabajadores del supervisor de trabajadores para transferir
   * un trabajador que indica dicho número.
   */
  case class NumberWorkersTransferWorker(sender: ActorRef, request: TransferWorkerRQ, numWorkers: Int)

}

/**
 * Supervisor de los actores de las oficinas.
 */
class OfficesSupervisor extends CommonActor {

  /**
   * Procesa los errores producidos por las oficinas.
   */
  override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy(loggingEnabled = false) {
    case e: RecoveryStateException =>
      logger.error(LogConstants.SYSTEM_NOT_RECOVERED.concat(e.message), e)
      Stop
  }

  /**
   * Maneja los mensajes recibidos por el supervisor.
   */
  def receive: Receive = {
    case msg: CreateOffice => process(msg)
    case msg: CreateWorkerRQ => requestGatherer(sender(), msg)
    case msg: AssignWorkerRQ => requestGatherer(sender(), msg)
    case msg: TransferWorkerRQ => sendToWorkersSupervisor(GiveNumberWorkersTransferWorker(sender(), msg))
    case msg: NumberWorkersTransferWorker => process(msg)
    case msg: GiveInfoRedRQ => requestGatherer(sender(), msg)
    case msg: GathererBuilt => process(msg)
    case msg => printUnknownMsg(msg)
  }

  /**
   * Procesa el mensaje [[CreateOffice]]
   */
  private def process(msg: CreateOffice) = {
    val actorName = CoreConstants.OFFICE_PREFIX.concat(msg.code)
    context.actorOf(Office.props(msg.regionalCode, msg.name, msg.code), actorName)
  }

  /**
   * Procesa el mensaje [[NumberWorkersTransferWorker]]
   */
  private def process(msg: NumberWorkersTransferWorker) = {
    val build = BuildGatherer(msg.sender, numOffices + msg.numWorkers, msg.request)
    context.actorSelection(SystemActors.gathererBuilderPath) ! build
  }

  /**
   * Procesa el mensaje [[GathererBuilt]]
   */
  private def process(built: GathererBuilt) = built.request match {
    case TransferWorkerRQ(login, codigoOficina) =>
      sendToOffices(CheckOfficeExists(codigoOficina, built.gatherer))
      sendToWorkersSupervisor(CheckTransfer(login, codigoOficina, built.gatherer))

    case _ => sendToOffices(messageByGathererBuilt(built))
  }

  /**
   * Retorna el mensaje con el gatherer que debe ser enviado a las oficinas dada la solicitud original que se busca
   * atender.
   */
  private def messageByGathererBuilt(built: GathererBuilt) = built.request match {
    case CreateWorkerRQ(codigoOficina, login, rol) => CheckOfficeExists(codigoOficina, built.gatherer)
    case AssignWorkerRQ(codigoOficina, numeroSolicitudes, rol, codigoProceso) => GiveWorkersWithCapacity(codigoOficina, rol, numeroSolicitudes, built.gatherer)
    case GiveInfoRedRQ() => GiveOfficeInfo(built.gatherer)
    case _ => None // Solo para que sea exhaustivo
  }

  /**
   * Solicita la instancia del gatherer que atiende la solicitud dada.
   */
  private def requestGatherer(sender: ActorRef, msg: Request) = {
    context.actorSelection(SystemActors.gathererBuilderPath) ! BuildGatherer(sender, numOffices, msg)
  }

  /**
   * Envía el mensaje dado a las oficinas que supervisa éste.
   *
   * @param msg Mensaje que se enviará.
   */
  private def sendToOffices(msg: Any) = context.children.foreach(_ ! msg)

  /**
   * Retorna el número de oficinas que supervisa éste.
   */
  private def numOffices = context.children.size
}
