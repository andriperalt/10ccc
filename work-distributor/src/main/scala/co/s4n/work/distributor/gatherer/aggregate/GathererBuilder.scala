package co.com.sura.distribucion.asignacion.node.gatherer.aggregate

import akka.actor.ActorRef

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate._
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.gatherer.aggregate.GathererBuilder.{ AssignGathererFounded, BuildGatherer }
import co.com.sura.distribucion.asignacion.node.roles.aggregate.RolesInfo.GiveAssignGathererObject
import co.com.sura.distribucion.asignacion.node.roles.aggregate.gatherers.GiveInfoRedGatherer
import co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers._

/**
 * Define los mensajes específicos del constructor de gatherers.
 */
object GathererBuilder {

  /**
   * Comando que inicia la creación de un gatherer.
   *
   * @param listener Actor al que se le responderá.
   * @param maxResponses Máximo número de respuestas que se esperan recibir.
   * @param request Solicitud que se atenderá.
   */
  case class BuildGatherer(listener: ActorRef, maxResponses: Int, request: Request) extends Command

  /**
   * Respuesta a una solicitud de recolección del objeto acompañante del gatherer que implementa el algoritmo de
   * asignación del rol y proceso dados que indica el gatherer esta definido.
   *
   * @param originalSender Actor que inicio la recolección.
   * @param listener Actor al que se le responderá.
   * @param maxResponses Máximo número de respuestas que se esperan recibir.
   * @param request Solicitud que se atenderá.
   * @param gathererObject Objeto acompañante del gatherer.
   */
  case class AssignGathererFounded(originalSender: ActorRef, listener: ActorRef, maxResponses: Int, request: Request, gathererObject: GathererObject)

}

/**
 * Constructor de gatherers.
 */
class GathererBuilder extends CommonActor {

  /**
   * Maneja los mensajes recibidos por el actor.
   */
  def receive: Receive = {
    case msg: BuildGatherer => process(msg)
    case AssignGathererFounded(originalSender, listener, maxExpectedResponses, request, gathererObject) =>
      sendGatherer(originalSender, listener, maxExpectedResponses, request, gathererObject)
    case msg => printUnknownMsg(msg)
  }

  // scalastyle:off cyclomatic.complexity
  /**
   * Procesa el mensaje [[BuildGatherer]].
   */
  private def process(msg: BuildGatherer) = {
    val gatherer = msg.request match {
      case rq: CreateWorkerRQ => Option(CreateWorkerGatherer)

      case rq: AssignWorkerRQ =>
        processAssignWorkerRQ(msg, rq)
        Option.empty

      case rq: DisableWorkerRQ => Option(DisableWorkerGatherer)
      case rq: EnableWorkerRQ => Option(EnableWorkerGatherer)
      case rq: DeleteWorkerRQ => Option(DeleteWorkerGatherer)
      case rq: TransferWorkerRQ => Option(TransferWorkerGatherer)
      case rq: TransferAssignmentsRQ => Option(TransferAssignmentsGatherer)
      case rq: UnassignWorkerRQ => Option(UnassignWorkerGatherer)
      case rq: GiveInfoRedRQ => Option(GiveInfoRedGatherer)
      case rq: ChangeWorkerRoleRQ => Option(ChangeRoleWorkerGatherer)
      case _ => Option.empty // Solo para que sea exhaustivo
    }
    gatherer.foreach(selected => sendGatherer(sender(), msg.listener, msg.maxResponses, msg.request, selected))
  }
  // scalastyle:on cyclomatic.complexity

  /**
   * Procesa la solicitud [[AssignWorkerRQ]].
   */
  private def processAssignWorkerRQ(msg: BuildGatherer, request: AssignWorkerRQ) = {
    val gatherer = GiveAssignGathererObject(sender(), msg.listener, msg.maxResponses, request, request.rol, request.codigoProceso)
    sendToRolesInfo(gatherer)
  }

  /**
   * Construye y envía el actor creado con el [[GathererObject]] dado.
   */
  private def sendGatherer(sender: ActorRef, listener: ActorRef, maxResponses: Int, request: Request, gatherer: GathererObject) = {
    sender ! GathererBuilt(request, context.actorOf(gatherer.props(listener, maxResponses, request)))
  }
}
