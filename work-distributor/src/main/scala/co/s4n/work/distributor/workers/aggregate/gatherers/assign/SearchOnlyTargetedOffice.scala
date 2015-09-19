package co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.assign

import akka.actor.{ ActorRef, Props }

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate._
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.workers.aggregate.Worker.Assign

/**
 * Fábrica de instancias de [[SearchOnlyTargetedOffice]].
 */
object SearchOnlyTargetedOffice extends GathererObject {

  /**
   * Retorna una instancia [[Props]] del actor [[SearchOnlyTargetedOffice]].
   *
   * @param listener Actor del servicio al que se le responderá finalmente.
   * @param maxResponses Máximo número de respuestas totales que se esperan recibir.
   * @param request Solicitud original que llega del servicio.
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(SearchOnlyTargetedOffice(listener, expectedResponses = 1, maxResponses, request.asInstanceOf[AssignWorkerRQ]))
  }
}

/**
 * Acumula las posibles respuestas al evento de asignación de un trabajador y genera una respuesta final.
 *
 * Pasos:
 * 1. Valida que la oficina exista.
 * 1. Valida que existan trabajadores.
 * 2. Colecta los posibles trabajadores a asignar.
 * 3. Elige el trabajador con menor carga.
 * 3. Inicia la asignación del trabajador.
 *
 * Posibles respuestas:
 * [[OfficeNotExists]]
 * - Si la oficina no existe.
 * [[WorkerSuccessfullyAssigned]]
 * - Si se asigna exitosamente el trabajador.
 * [[NotOneWithCapacity]]
 * - Si no se encontró ningún trabajador para asignar.
 */
case class SearchOnlyTargetedOffice(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: AssignWorkerRQ) extends Gatherer {

  val defaultResponse = OfficeNotExists

  /**
   * Variables que controlan la búsqueda en la oficina original.
   */
  private var workers = Map.empty[String, Int]
  private var workerIDSelected = Option.empty[String]

  /**
   * Maneja los mensajes enviados por las oficinas.
   */
  def receive: Receive = {
    case response: NotWorkersWithCapacity => processOriginalOfficeResponse(response)
    case response: WorkersWithCapacity => processOriginalOfficeResponse(response)
    case NotMe => // Ignorar
    case msg => printUnknownMsg(msg)
  }

  /**
   * Maneja los mensajes enviados por los trabajadores.
   */
  private def receiveWorker: Receive = {
    case WorkerCannotBeAssigned =>
      if (workers.nonEmpty) {
        removeLastWorkerSelected()
        tryAssignWorker()
      } else {
        processOriginalOfficeResponse(WorkerCannotBeAssigned)
      }

    case response: WorkerSuccessfullyAssigned => sendFinalResponse(response)
  }

  /**
   * Procesa la respuesta de la oficina original donde se intento asignar.
   */
  private def processOriginalOfficeResponse(response: Any) = response match {
    case response: NotWorkersWithCapacity => sendFinalResponse(NotOneWithCapacity)

    case response: WorkersWithCapacity =>
      workers = response.workers
      context.become(receiveWorker)
      tryAssignWorker()

    case WorkerCannotBeAssigned => sendFinalResponse(NotOneWithCapacity)
  }

  /**
   * Busca el identificador del trabajador con menor número de asignaciones dentro de la lista [[workers]] y le envía el
   * comando para asignar.
   */
  private def tryAssignWorker() = {
    val workerID = searchLowestWorkerID()
    workerIDSelected = Some(workerID)
    sendToWorker(Assign(request.numeroSolicitudes, self), workerID)
  }

  /**
   * Retorna el identificador del trabajador con menor número de asignaciones.
   */
  private def searchLowestWorkerID() = searchLowest(workers)

  /**
   * Retorna el código o ID de la entidad con menos asignaciones.
   */
  private def searchLowest(data: Map[String, Int]) = {
    val (foundID, _) = data.toList.reduceLeft(minAssignments)
    foundID
  }

  /**
   * Retorna el código o ID con menos número de asignaciones.
   *
   * Cada tuplas esta compuesta de: (código o ID, número de asignaciones).
   */
  private def minAssignments(tuple1: (String, Int), tuple2: (String, Int)) = {
    val (_, numAssignments1) = tuple1
    val (_, numAssignments2) = tuple2
    if (numAssignments1 < numAssignments2) tuple1 else tuple2
  }

  /**
   * Remueve el último trabajador que se intentó asignar.
   */
  private def removeLastWorkerSelected() = workers = removeLastSelected(workers, workerIDSelected)

  /**
   * Remueve el elemento de la lista dada.
   */
  private def removeLastSelected(list: Map[String, Int], selected: Option[String]) = list - selected.getOrElse("")
}
