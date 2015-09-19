package co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers

import akka.actor.{ ActorRef, Props }

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ Gatherer, GathererObject, NotMe }
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.workers.aggregate.Worker.TransferAssignments
import co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.TransferAssignmentsGatherer.WorkerTransferAssignments

/**
 * Fábrica de instancias de [[TransferAssignmentsGatherer]].
 * Define los mensajes específicos involucrados en la transferencia de asignaciones de un trabajador a otro.
 */
object TransferAssignmentsGatherer extends GathererObject {

  /**
   * Retorna Props del actor [[TransferAssignmentsGatherer]].
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(TransferAssignmentsGatherer(listener, expectedResponses = 2, maxResponses, request.asInstanceOf[TransferAssignmentsRQ]))
  }

  /**
   * Respuesta a una solicitud de transferencia de asignaciones que indica la información de los trabajadores
   * involucrados.
   */
  case class WorkerTransferAssignments(workerID: String, workerRole: String)

}

/**
 * Acumula las posibles respuestas al comando de transferencia de asignaciones de un trabajador a otro y genera una
 * respuesta final.
 *
 * Pasos:
 * 1. Busca que los trabajadores involucrados.
 * 2. Inicia la transferencia de asignaciones en la oficina del trabajador destino.
 * 3. Inicia la eliminación de asignaciones en el trabajador origen.
 *
 * Posibles respuestas:
 * [[WorkerNotExists]]
 * - Si ningún trabajador existe.
 * [[WorkerNotEnoughAssignments]]
 * - Si el trabajador de origen no tiene suficientes asignaciones para transferir.
 * [[WorkerTransferAssignmentsNotExists]]
 * - Si alguno de los trabajadores no existe.
 * [[WorkersHaveDifferentRoles]]
 * - Si el trabajador destino no tiene el mismo rol que el trabajador origen.
 * [[AssignmentsWorkerDestinationOverLimit]]
 * - Si la oficina del trabajador destino no tiene capacidad para las nuevas asignaciones para el rol del trabajador
 * origen.
 * [[AssignmentsSuccessfullyTransferred]]
 * - Si se transfieren exitosamente las asignaciones.
 */
case class TransferAssignmentsGatherer(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: TransferAssignmentsRQ) extends Gatherer {

  val defaultResponse = WorkerNotExists

  private var responsesOK = Seq.empty[Any]
  private var originWorker: Option[WorkerTransferAssignments] = None
  private var destinationWorker: Option[WorkerTransferAssignments] = None

  /**
   * Maneja los mensajes recibidos por los actores que son en los que el gatherer esta interesado.
   */
  def receive: Receive = {
    case response: WorkerNotEnoughAssignments => addResponse(response)
    case response: AssignmentsWorkerDestinationOverLimit => addResponse(response)
    case response: WorkerTransferAssignments => addWorker(response)
    case AssignmentsSuccessfullyTransferred => reviewSendFinalSuccessfully()
    case NotMe => // Ignorar
    case msg => printUnknownMsg(msg)
  }

  /**
   * Agrega la respuesta dada a la lista y revisa si ya se recibieron todas.
   */
  private def addResponse(response: Any) = {
    responsesOK = responsesOK :+ response
    reviewResponses()
  }

  /**
   * Agrega el trabajador que ha respondido.
   */
  private def addWorker(response: WorkerTransferAssignments) = {
    if (response.workerID.equals(request.loginOrigen)) {
      originWorker = Some(response)
    } else {
      destinationWorker = Some(response)
    }
    addResponse(response)
  }

  /**
   * Revisa si ya se transfirieron las asignaciones de ambos trabajadores.
   */
  private def reviewSendFinalSuccessfully() = {
    if (destinationWorker.nonEmpty) {
      val workerID = destinationWorker.get.workerID
      destinationWorker = None
      sendToWorker(TransferAssignments(request.numeroSolicitudes, self), workerID)
    } else {
      sendFinalResponse(AssignmentsSuccessfullyTransferred)
    }
  }

  /**
   * Revisa si ya se obtuvieron todas las respuestas.
   */
  private def reviewResponses() = {
    val numResponsesOK = responsesOK.size
    if (numResponsesOK == responses) {
      if (numResponsesOK == 1) {
        processOneResponse(responsesOK.head)
      } else if (numResponsesOK == 2) {
        processTwoResponses()
      }
    }
  }

  /**
   * Procesa la única respuesta que se recibió.
   */
  private def processOneResponse(response: Any) = response match {
    case response: WorkerNotEnoughAssignments => sendFinalResponse(response)
    case response: AssignmentsWorkerDestinationOverLimit => sendFinalResponse(response)
    case response: WorkerTransferAssignments =>
      if (response.workerID.equals(request.loginOrigen)) {
        sendFinalResponse(WorkerTransferAssignmentsNotExists(request.loginDestino))
      } else {
        sendFinalResponse(WorkerTransferAssignmentsNotExists(request.loginOrigen))
      }
  }

  /**
   * Procesa respuestas que se recibieron.
   */
  private def processTwoResponses() = {
    responsesOK.find { x =>
      x.isInstanceOf[WorkerNotEnoughAssignments] || x.isInstanceOf[AssignmentsWorkerDestinationOverLimit]
    } match {
      case Some(response) =>
        response match {
          case rsp: WorkerNotEnoughAssignments => sendFinalResponse(rsp)
          case rsp: AssignmentsWorkerDestinationOverLimit => sendFinalResponse(rsp)
        }
      case None => processWorkersFound()
    }
  }

  /**
   * Procesa las respuestas cuando ambos trabajadores existen.
   */
  private def processWorkersFound() = {
    if (originWorker.get.workerRole.equals(destinationWorker.get.workerRole)) {
      sendToWorker(TransferAssignments(0 - request.numeroSolicitudes, self), originWorker.get.workerID)
    } else {
      sendFinalResponse(WorkersHaveDifferentRoles(originWorker.get.workerRole))
    }
  }
}
