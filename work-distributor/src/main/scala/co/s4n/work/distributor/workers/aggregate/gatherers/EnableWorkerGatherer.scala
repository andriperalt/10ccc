package co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers

import akka.actor._

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ Gatherer, GathererObject, NotMe }
import co.com.sura.distribucion.asignacion.node.commons.ws._

/**
 * Fábrica de instancias de [[EnableWorkerGatherer]].
 * Define los mensajes específicos involucrados en la habilitación de un trabajador.
 */
object EnableWorkerGatherer extends GathererObject {

  /**
   * Retorna Props del actor [[EnableWorkerGatherer]].
   *
   * @param listener Actor al que se le responderá.
   * @param maxResponses Máximo número de respuestas que se esperan obtener.
   * @param request Solicitud original que atiende el gatherer.
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(EnableWorkerGatherer(listener, expectedResponses = 1, maxResponses, request.asInstanceOf[EnableWorkerRQ]))
  }
}

/**
 * Acumula las posibles respuestas al evento de habilitación de un trabajador y genera una respuesta final.
 *
 * Pasos:
 * 1. Valida que el trabajador exista.
 * 2. Persiste el evento de habilitación en el trabajador.
 * 3. Habilita el trabajador.
 *
 * Posibles respuestas:
 * [[WorkerNotExists]]
 * - Si el trabajador que se intenta habilitar no existe.
 * [[WorkerAlreadyEnabled]]
 * - Si el trabajador que se intenta habilitar ya se encuentra habilitado.
 * [[WorkerSuccessfullyEnabled]]
 * - Si se habilita exitosamente el trabajador.
 */
case class EnableWorkerGatherer(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: EnableWorkerRQ) extends Gatherer {

  val defaultResponse = WorkerNotExists

  /**
   * Maneja los mensajes recibidos por el actor.
   */
  def receive: Receive = {
    case WorkerAlreadyEnabled => sendFinalResponse(WorkerAlreadyEnabled)
    case WorkerSuccessfullyEnabled => sendFinalResponse(WorkerSuccessfullyEnabled)
    case NotMe => // Ignorar
    case msg => printUnknownMsg(msg)
  }
}
