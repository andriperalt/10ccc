package co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers

import akka.actor._

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ Gatherer, GathererObject, NotMe }
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.offices.aggregate.Office.DeleteWorker
import co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.DeleteWorkerGatherer.WorkerDeleted

/**
 * Fábrica de instancias de [[DeleteWorkerGatherer]].
 * Define los mensajes específicos involucrados en la eliminación de un trabajador.
 */
object DeleteWorkerGatherer extends GathererObject {

  /**
   * Retorna Props del actor [[DeleteWorkerGatherer]].
   *
   * @param listener Actor al que se le responderá.
   * @param maxResponses Máximo número de respuestas que se esperan obtener.
   * @param request Solicitud original que atiende el gatherer.
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(DeleteWorkerGatherer(listener, expectedResponses = 1, maxResponses, request.asInstanceOf[DeleteWorkerRQ]))
  }

  /**
   * Respuesta a una solicitud de eliminación de un trabajador que indica el trabajador se eliminó.
   */
  case class WorkerDeleted(officeCode: String, workerRole: String)

}

/**
 * Acumula las posibles respuestas al evento de eliminación de un trabajador y genera una respuesta final.
 *
 * Pasos:
 * 1. Valida que el trabajador cumpla las condiciones para su eliminación.
 * 2. Elimina el trabajador de la oficina.
 * 3. Persiste el evento de eliminación en la oficina
 * 3. Elimina el estado del trabajador eliminado.
 *
 * Posibles respuestas:
 *
 * [[WorkerNotExists]]
 * - Si el trabajador que se intenta eliminar no existe.
 * [[WorkerHasAssignedTasks]]
 * - Si el trabajador que se intenta eliminar tiene tareas asignadas.
 * [[WorkerDeletedSuccessfully]]
 * - Si se elimina exitosamente el trabajador.
 */
case class DeleteWorkerGatherer(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: DeleteWorkerRQ) extends Gatherer {

  val defaultResponse = WorkerNotExists

  /**
   * Maneja los mensajes recibidos por el actor.
   */
  def receive: Receive = {
    case rsp: WorkerHasAssignedTasks => sendFinalResponse(rsp)
    case WorkerDeleted(officeCode, workerRole) => sendToOffice(DeleteWorker(request.login, workerRole), officeCode)
    case WorkerDeletedSuccessfully => sendFinalResponse(WorkerDeletedSuccessfully)
    case NotMe => // Ignorar
    case msg => printUnknownMsg(msg)
  }
}
