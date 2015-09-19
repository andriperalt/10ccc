package co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers

import akka.actor.{ ActorRef, Props }

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ Gatherer, GathererObject, NotMe }
import co.com.sura.distribucion.asignacion.node.commons.ws._

/**
 * Fábrica de instancias de [[UnassignWorkerGatherer]].
 * Define los mensajes específicos involucrados en la remoción de solicitudes de un trabajador.
 */
object UnassignWorkerGatherer extends GathererObject {

  /**
   * Retorna Props del actor [[UnassignWorkerGatherer]].
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(UnassignWorkerGatherer(listener, expectedResponses = 1, maxResponses, request.asInstanceOf[UnassignWorkerRQ]))
  }

}

/**
 * Acumula las posibles respuestas al comando de remoción de solicitudes de un trabajador. y genera una respuesta
 * final.
 *
 * Pasos:
 * 1. Busca el trabajador.
 * 2. Inicia la remoción de solicitudes del trabajador encontrado.
 *
 * Posibles respuestas:
 * [[WorkerNotExists]]
 * - Si el trabajador no existe.
 * [[WorkerNotEnoughAssignments]]
 * - Si el trabajador no tiene suficientes solicitudes asignadas para remover.
 * [[AssignmentsSuccessfullyUnassigned]]
 * - Si se removieron exitosamente las solicitudes.
 */
case class UnassignWorkerGatherer(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: UnassignWorkerRQ) extends Gatherer {

  val defaultResponse = WorkerNotExists

  /**
   * Maneja los mensajes recibidos por los actores que son en los que el gatherer esta interesado.
   */
  def receive: Receive = {
    case msg: WorkerNotEnoughAssignments => sendFinalResponse(msg)
    case AssignmentsSuccessfullyUnassigned => sendFinalResponse(AssignmentsSuccessfullyUnassigned)
    case NotMe => // Ignorar
    case msg => printUnknownMsg(msg)
  }
}
