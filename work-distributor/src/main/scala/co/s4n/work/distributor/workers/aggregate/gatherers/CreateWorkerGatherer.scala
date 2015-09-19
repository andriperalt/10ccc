package co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers

import akka.actor.{ ActorRef, Props }

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate._
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.offices.aggregate.Office.CreateWorker
import co.com.sura.distribucion.asignacion.node.roles.aggregate.RolesInfo.GiveMaxAssignments
import co.com.sura.distribucion.asignacion.node.workers.aggregate.WorkersSupervisor.CreateSupervisedWorker
import co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.CreateWorkerGatherer.WorkerSupervisedCreatedSuccessfully

/**
 * Fábrica de instancias de [[CreateWorkerGatherer]].
 * Define los mensajes específicos involucrados en la creación de un trabajador.
 */
object CreateWorkerGatherer extends GathererObject {

  /**
   * Retorna Props del actor [[CreateWorkerGatherer]].
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(CreateWorkerGatherer(listener, expectedResponses = 1, maxResponses, request.asInstanceOf[CreateWorkerRQ]))
  }

  /**
   * Respuesta a una solicitud de creación de un trabajador que indica se creó exitosamente el trabajador en el
   * supervisor de trabajadores.
   */
  case object WorkerSupervisedCreatedSuccessfully

}

/**
 * Acumula las posibles respuestas al evento de creación de un trabajador transferido a otra oficina y genera una
 * respuesta final.
 *
 * Pasos:
 * 1. Valida que la oficina donde se creará el trabajador exista.
 * 2. Crea el trabajador en el supervisor de trabajadores.
 * 3. Crea el trabajador en oficina.
 *
 * Posibles respuestas:
 * [[OfficeNotExists]]
 * - Si no existe la oficina.
 * [[RoleUndefined]]
 * - Si no existe el rol del trabajador.
 * [[WorkerExists]]
 * - Si el trabajador que se intenta crear ya existe.
 * [[CreateWorkerUnsuccessful]]
 * - Si hay algún problema inesperado al crear el actor del trabajador.
 * [[WorkerCreatedSuccessfully]]
 * - Si se crea exitosamente el trabajador en la oficina.
 */
case class CreateWorkerGatherer(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: CreateWorkerRQ) extends Gatherer {

  val defaultResponse = OfficeNotExists

  private var maxAssignments = Option.empty[Int]

  /**
   * Maneja los mensajes recibidos por el actor.
   */
  def receive: Receive = {
    case OfficeExists => sendToRolesInfo(GiveMaxAssignments(request.rol))
    case RoleUndefined => sendFinalResponse(RoleUndefined)

    case RoleMaxAssignments(max) =>
      maxAssignments = Option(max)
      sendToWorkersSupervisor(CreateSupervisedWorker(request.codigoOficina, request.login, request.rol, max))

    case WorkerExists => sendFinalResponse(WorkerExists)
    case rsp: CreateWorkerUnsuccessful => sendFinalResponse(rsp)
    case WorkerSupervisedCreatedSuccessfully => sendToOffice(CreateWorker(request.login, request.rol, maxAssignments.get), request.codigoOficina)
    case WorkerCreatedSuccessfully => sendFinalResponse(WorkerCreatedSuccessfully)
    case NotMe => // Ignorar
    case msg => printUnknownMsg(msg)
  }
}
