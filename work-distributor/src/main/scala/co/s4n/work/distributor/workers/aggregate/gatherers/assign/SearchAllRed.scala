package co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.assign

import akka.actor.{ ActorRef, Props }

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate._
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.offices.aggregate.Office.GiveWorkersWithCapacity
import co.com.sura.distribucion.asignacion.node.red.aggregate.Red.GiveRegionalsWithCapacity
import co.com.sura.distribucion.asignacion.node.regionals.aggregate.Regional.GiveOfficesWithCapacity
import co.com.sura.distribucion.asignacion.node.workers.aggregate.Worker.Assign

/**
 * Fábrica de instancias de [[SearchAllRed]].
 * Define los mensajes específicos involucrados en la asignación de un trabajador.
 */
object SearchAllRed extends GathererObject {

  /**
   * Retorna Props del actor [[SearchAllRed]].
   *
   * @param listener Actor al que se le responderá.
   * @param maxResponses Máximo número de respuestas que se esperan obtener.
   * @param request Solicitud original que atiende el gatherer.
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(SearchAllRed(listener, expectedResponses = 1, maxResponses, request.asInstanceOf[AssignWorkerRQ]))
  }
}

/**
 * Acumula las posibles respuestas al evento de asignación de un trabajador y genera una respuesta final.
 *
 * Pasos:
 * 1. Valida que la oficina exista.
 * 2. Valida que existan trabajadores.
 * 3. Colecta los posibles trabajadores a asignar.
 * 4. Elige el trabajador con menor carga.
 * 5. Inicia la asignación del trabajador.
 *
 * Posibles respuestas:
 * - [[OfficeNotExists]]: Si la oficina no existe.
 * - [[WorkerSuccessfullyAssigned]]: Si se asigna exitosamente el trabajador.
 * - [[NotOneWithCapacity]]: Si no se encontró ningún trabajador para asignar.
 */
case class SearchAllRed(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: AssignWorkerRQ) extends Gatherer {

  val defaultResponse = OfficeNotExists

  /**
   * Variables que controlan la búsqueda en la oficina original.
   */
  private var lookingAtOriginalOffice = true
  private var workers = Map.empty[String, Int]
  private var workerIDSelected = Option.empty[String]

  /**
   * Variables que controlan la búsqueda en la regional de la oficina original.
   */
  private var lookingAtOriginalRegional = false
  private var offices = Map.empty[String, Int]
  private var officeCodeSelected = Option.empty[String]

  /**
   * Variables que controlan la búsqueda en las regionales de la red.
   */
  private var regionals = Map.empty[String, Int]
  private var regionalCodeSelected = Option.empty[String]

  /**
   * Maneja los mensajes enviados por las oficinas.
   */
  def receive: Receive = {
    case response: NotWorkersWithCapacity =>
      if (lookingAtOriginalOffice) {
        processOriginalOfficeResponse(response)
      } else if (lookingAtOriginalRegional) {
        processOriginalRegionalOfficesResponse(response)
      } else {
        processRedRegionalOfficesResponse(response)
      }

    case response: WorkersWithCapacity =>
      if (lookingAtOriginalOffice) {
        processOriginalOfficeResponse(response)
      } else if (lookingAtOriginalRegional) {
        processOriginalRegionalOfficesResponse(response)
      } else {
        processRedRegionalOfficesResponse(response)
      }

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
        if (lookingAtOriginalOffice) {
          processOriginalOfficeResponse(WorkerCannotBeAssigned)
        } else if (lookingAtOriginalRegional) {
          processOriginalRegionalOfficesResponse(WorkerCannotBeAssigned)
        } else {
          processRedRegionalOfficesResponse(WorkerCannotBeAssigned)
        }
      }

    case response: WorkerSuccessfullyAssigned => sendFinalResponse(response)
  }

  /**
   * Maneja los mensajes enviados por las regionales.
   */
  private def receiveRegional: Receive = {
    case response: OfficesWithCapacity =>
      if (lookingAtOriginalRegional) {
        processOriginalRegionalResponse(response)
      } else {
        processRedRegionalsResponse(response)
      }

    case NotOfficesWithCapacity =>
      if (lookingAtOriginalRegional) {
        processOriginalRegionalResponse(NotOfficesWithCapacity)
      } else {
        processRedRegionalsResponse(NotOfficesWithCapacity)
      }
  }

  /**
   * Maneja los mensajes enviados por la red.
   */
  private def receiveRed: Receive = {
    case NotRegionalsWithCapacity => sendFinalResponse(NotOneWithCapacity)

    case response: RegionalsWithCapacity =>
      regionals = response.regionals
      context.become(receiveRegional)
      tryAssignRegional()
  }

  /**
   * Procesa la respuesta de la oficina original donde se intento asignar.
   */
  private def processOriginalOfficeResponse(response: Any) = response match {
    case response: NotWorkersWithCapacity =>
      regionalCodeSelected = Some(response.regionalID)
      changeToRegional()

    case response: WorkersWithCapacity =>
      regionalCodeSelected = Some(response.regionalID)
      officeCodeSelected = Some(request.codigoOficina)
      workers = response.workers
      context.become(receiveWorker)
      tryAssignWorker()

    case WorkerCannotBeAssigned =>
      workerIDSelected = None
      changeToRegional()
  }

  /**
   * Procesa la respuesta de la regional original donde se intento asignar.
   */
  private def processOriginalRegionalResponse(response: Any) = response match {
    case response: OfficesWithCapacity =>
      offices = response.offices
      context.become(receive)
      tryAssignOffice()

    case NotOfficesWithCapacity => changeToRed()
  }

  /**
   * Procesa la respuesta de la oficinas oficinas de la regional original donde se intento asignar.
   */
  private def processOriginalRegionalOfficesResponse(response: Any) = response match {
    case response: NotWorkersWithCapacity => tryNextOffice()

    case response: WorkersWithCapacity =>
      workers = response.workers
      context.become(receiveWorker)
      tryAssignWorker()

    case WorkerCannotBeAssigned => tryNextOffice()
  }

  /**
   * Procesa la respuesta de la oficinas oficinas de la regional original donde se intento asignar.
   */
  private def processRedRegionalsResponse(response: Any) = response match {
    case response: OfficesWithCapacity =>
      offices = response.offices
      context.become(receive)
      tryAssignOffice()

    case NotOfficesWithCapacity => tryNextRegional()
  }

  /**
   * Procesa la respuesta de la oficinas oficinas de la regional original donde se intento asignar.
   */
  private def processRedRegionalOfficesResponse(response: Any) = response match {
    case response: NotWorkersWithCapacity => tryNextRedRegionalOffice()

    case response: WorkersWithCapacity =>
      workers = response.workers
      context.become(receiveWorker)
      tryAssignWorker()

    case WorkerCannotBeAssigned => tryNextRedRegionalOffice()
  }

  /**
   * Cambia a búsqueda en la regional.
   */
  private def changeToRegional() = {
    lookingAtOriginalOffice = false
    lookingAtOriginalRegional = true
    context.become(receiveRegional)
    sendToRegional(GiveOfficesWithCapacity(request.codigoOficina, request.rol, request.numeroSolicitudes), regionalCodeSelected.get)
  }

  /**
   * Cambia a búsqueda en toda la red.
   */
  private def changeToRed() = {
    lookingAtOriginalRegional = false
    context.become(receiveRed)
    sendToRed(GiveRegionalsWithCapacity(regionalCodeSelected.get, request.rol, request.numeroSolicitudes))
  }

  /**
   * Si la lista de oficinas no esta vacía intenta asignar la siguiente, de lo contrario empieza a buscar en la red.
   */
  private def tryNextOffice() = {
    if (offices.nonEmpty) {
      removeLastOfficeSelected()
      context.become(receive)
      tryAssignOffice()
    } else {
      changeToRed()
    }
  }

  /**
   * Si la lista de oficinas no esta vacía intenta asignar la siguiente, de lo contrario empieza a buscar en la red.
   */
  private def tryNextRedRegionalOffice() = {
    if (offices.nonEmpty) {
      removeLastOfficeSelected()
      context.become(receive)
      tryAssignOffice()
    } else {
      tryNextRegional()
    }
  }

  /**
   * Si la lista de oficinas no esta vacía intenta asignar la siguiente, de lo contrario empieza a buscar en la red.
   */
  private def tryNextRegional() = {
    if (regionals.nonEmpty) {
      removeLastRegionalSelected()
      context.become(receiveRegional)
      tryAssignRegional()
    } else {
      sendFinalResponse(NotOneWithCapacity)
    }
  }

  /**
   * Busca la regional con menor número de asignaciones dentro de la lista [[regionals]] y le envía el comando para dar
   * la lista de oficinas para asignar.
   */
  private def tryAssignRegional(): Unit = {
    val regionalCode = searchLowestRegionalCode()
    regionalCodeSelected = Some(regionalCode)
    sendToRegional(GiveOfficesWithCapacity(request.codigoOficina, request.rol, request.numeroSolicitudes), regionalCode)
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
   * Busca la oficina con menor número de asignaciones dentro de la lista [[offices]] y le envía el comando para dar la
   * lista de trabajadores para asignar.
   */
  private def tryAssignOffice() = {
    val officeCode = searchLowestOfficeCode()
    officeCodeSelected = Some(officeCode)
    sendToOffice(GiveWorkersWithCapacity(officeCode, request.rol, request.numeroSolicitudes, self), officeCode)
  }

  /**
   * Retorna el identificador del trabajador con menor número de asignaciones.
   */
  private def searchLowestWorkerID() = searchLowest(workers)

  /**
   * Retorna el identificador de la oficina con menor número de asignaciones.
   */
  private def searchLowestOfficeCode() = searchLowest(offices)

  /**
   * Retorna el código de la regional con menos asignaciones.
   */
  private def searchLowestRegionalCode() = searchLowest(regionals)

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
   * Remueve la última oficina que se intentó asignar.
   */
  private def removeLastOfficeSelected() = offices = removeLastSelected(offices, officeCodeSelected)

  /**
   * Remueve la última regional que se intentó asignar.
   */
  private def removeLastRegionalSelected() = regionals = removeLastSelected(regionals, regionalCodeSelected)

  /**
   * Remueve el elemento de la lista dada.
   */
  private def removeLastSelected(list: Map[String, Int], selected: Option[String]) = list - selected.getOrElse("")
}
