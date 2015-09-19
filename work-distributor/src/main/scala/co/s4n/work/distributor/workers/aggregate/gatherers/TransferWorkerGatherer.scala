package co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers

import akka.actor._

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ Gatherer, GathererObject, NotMe, OfficeExists }
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.offices.aggregate.Office.{ CreateWorker, DeleteWorker }
import co.com.sura.distribucion.asignacion.node.workers.aggregate.Worker.Transfer
import co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.TransferWorkerGatherer.WorkerTransfer

/**
 * Fábrica de instancias de [[TransferWorkerGatherer]].
 * Define los mensajes específicos involucrados en la transferencia de un trabajador a otra oficina.
 */
object TransferWorkerGatherer extends GathererObject {

  /**
   * Retorna Props del actor [[TransferWorkerGatherer]].
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(TransferWorkerGatherer(listener, expectedResponses = 2, maxResponses, request.asInstanceOf[TransferWorkerRQ]))
  }

  /**
   * Respuesta a una solicitud de transferencia de oficina de un trabajador que indica la información del trabajador
   * que se desea transferir.
   */
  case class WorkerTransfer(workerOfficeCode: String, workerRoleName: String, workerMaxAssignments: Int)
}

/**
 * Acumula las posibles respuestas al evento de transferencia de un trabajador a otra oficina y genera una respuesta
 * final.
 *
 * Pasos:
 * 1. Valida que el trabajador se pueda transferir.
 * 2. Actualiza el estado de la oficina vieja eliminando el trabajador.
 * 3. Inicia la creación del trabajador en la nueva oficina.
 *
 * Posibles respuestas:
 * [[WorkerNotExists]]
 * - Si el trabajador que se intenta transferir no existe.
 * [[OfficeNotExists]]
 * - Si la oficina a la que se intenta transferir el trabajador no existe.
 * [[WorkerAlreadyThere]]
 * - Si el trabajador que se intenta transferir ya se encuentra en la oficina a donde se quiere llevar.
 * [[WorkerSuccessfullyTransferred]]
 * - Si se transfiere exitosamente el trabajador.
 */
case class TransferWorkerGatherer(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: TransferWorkerRQ) extends Gatherer {

  val defaultResponse = WorkerNotExists

  private var workerOffice: Option[String] = None
  private var workerRole: Option[String] = None
  private var workerMaxAssignments: Option[Int] = None
  private var responsesOK = Seq.empty[Any]

  /**
   * Maneja los mensajes recibidos por el actor.
   */
  def receive: Receive = {
    case WorkerAlreadyThere => addResponse(WorkerAlreadyThere)
    case OfficeExists => addResponse(OfficeExists)

    case msg: WorkerTransfer =>
      workerOffice = Some(msg.workerOfficeCode)
      workerRole = Some(msg.workerRoleName)
      workerMaxAssignments = Some(msg.workerMaxAssignments)
      addResponse(msg)

    case WorkerDeletedSuccessfully => sendToOffice(CreateWorker(request.login, workerRole.get, workerMaxAssignments.get), request.codigoOficina)
    case WorkerCreatedSuccessfully => sendToWorker(Transfer(request.codigoOficina, self), request.login)
    case WorkerSuccessfullyTransferred => sendFinalResponse(WorkerSuccessfullyTransferred)
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
   * Revisa si ya se obtuvieron todas las respuestas y si no existe el trabajador o la oficina envía el mensaje acordé,
   * de lo contrario continúa la transferencia.
   */
  private def reviewResponses() = {
    val numResponsesOK = responsesOK.size
    if (numResponsesOK == responses) {
      if (numResponsesOK == 1) {
        responsesOK.head match {
          case WorkerAlreadyThere => sendFinalResponse(WorkerAlreadyThere)
          case OfficeExists => sendFinalResponse(WorkerNotExists)
          case _: WorkerTransfer => sendFinalResponse(OfficeNotExists)
        }
      } else if (numResponsesOK == 2) {
        if (responsesOK.contains(WorkerAlreadyThere)) {
          sendFinalResponse(WorkerAlreadyThere)
        } else {
          sendToOffice(DeleteWorker(request.login, workerRole.get), workerOffice.get)
        }
      }
    }
  }
}
