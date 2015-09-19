package co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers

import akka.actor._

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ NotMe, Gatherer, GathererObject }
import co.com.sura.distribucion.asignacion.node.commons.ws._

/**
 * Fábrica de instancias de [[DisableWorkerGatherer]].
 * Define los mensajes específicos involucrados en la inhabilitación de un trabajador.
 */
object DisableWorkerGatherer extends GathererObject {

  /**
   * Retorna Props del actor [[DisableWorkerGatherer]].
   *
   * @param listener Actor al que se le responderá.
   * @param maxResponses Máximo número de respuestas que se esperan obtener.
   * @param request Solicitud original que atiende el gatherer.
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(DisableWorkerGatherer(listener, expectedResponses = 1, maxResponses, request.asInstanceOf[DisableWorkerRQ]))
  }
}

/**
 * Acumula las posibles respuestas al evento de inhabilitación de un trabajador y genera una respuesta final.
 *
 * Pasos:
 * 1. Valida que el trabajador exista.
 * 2. Persiste el evento de inhabilitación en el trabajador.
 * 3. In-habilita el trabajador.
 *
 * Posibles respuestas:
 * [[WorkerNotExists]]
 * - Si el trabajador que se intenta inhabilitar no existe.
 * [[WorkerAlreadyDisabled]]
 * - Si el trabajador que se intenta inhabilitar no existe.
 * [[WorkerHasAssignedTasks]]
 * - Si el trabajador que se intenta inhabilitar tiene tareas asignadas.
 * [[WorkerSuccessfullyDisabled]]
 * - Si se in-habilita exitosamente el trabajador.
 */
case class DisableWorkerGatherer(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: DisableWorkerRQ) extends Gatherer {

  val defaultResponse = WorkerNotExists

  /**
   * Maneja los mensajes recibidos por el actor.
   */
  def receive: Receive = {
    case msg: WorkerHasAssignedTasks => sendFinalResponse(msg)
    case WorkerAlreadyDisabled => sendFinalResponse(WorkerAlreadyDisabled)
    case WorkerSuccessfullyDisabled => sendFinalResponse(WorkerSuccessfullyDisabled)
    case NotMe => // Ignorar
    case msg => printUnknownMsg(msg)
  }
}
