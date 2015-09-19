package co.com.sura.distribucion.asignacion.node.workers.aggregate

import akka.actor.{ ActorRef, Cancellable, DeadLetter, Props }
import org.joda.time.DateTime

import scala.concurrent.duration._

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.core.SystemActors
import co.com.sura.distribucion.asignacion.node.commons.aggregate.ActorWithPersistence.{ ActorWithPersistenceState, SaveActorState }
import co.com.sura.distribucion.asignacion.node.commons.aggregate._
import co.com.sura.distribucion.asignacion.node.offices.aggregate.Office.{ BuildWorkerAssigned, UpdateWorkerAssignments, UpdateWorkerRole }
import co.com.sura.distribucion.asignacion.node.workers.aggregate.Worker._
import co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.CreateWorkerGatherer.WorkerSupervisedCreatedSuccessfully
import co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.DeleteWorkerGatherer.WorkerDeleted
import co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.TransferAssignmentsGatherer.WorkerTransferAssignments
import co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.TransferWorkerGatherer.WorkerTransfer

/**
 * Fábrica de instancias de [[Worker]]
 * Define los mensajes específicos procesados por un trabajador del sistema.
 */
object Worker {

  /**
   * Retorna Props del actor [[Worker]].
   */
  def props(ID: String): Props = Props(Worker(ID))

  /**
   * Estado del trabajador.
   */
  case class WorkerState(
      officeCode: Option[String] = None, maxAssignments: Int = 0, assignments: Int = 0, enabled: Boolean = true, selfEnablingDate: Option[DateTime] = None,
      schedulerSelfEnabling: Option[Cancellable] = None, role: String = ""
  ) extends ActorWithPersistenceState {
    /**
     * toString
     */
    override def toString: String = {
      val builder = new StringBuilder()
        .append(s"Código de la oficina: $officeCode\n")
        .append(s"Maximo numero de asignaciones actuales: $maxAssignments\n")
        .append(s"Numero de asignaciones actuales: $assignments\n")
        .append(s"Esta habilitado: ${if (enabled) "Si" else "NO"}\n")
        .append(s"El rol de el Trabajador es: $role\n")
      selfEnablingDate.foreach(v => builder.append(s"Fecha de autohabilitacion: $v\n"))
      builder.toString()
    }
  }

  /**
   * Comando que inicia la definición de la información inicial del trabajador como el código de la oficina a la que
   * pertenece y máximo número de asignaciones que puede recibir.
   */
  case class DefineWorkerInfo(officeCode: String, maxAssignments: Int, role: String) extends Command

  /**
   * Comando que inicia la actualización del máximo número de asignaciones del trabajador.
   */
  case class UpdateMaxAssignments(workerRole: String, maxAssignments: Int) extends Command

  /**
   * Comando que inicia la asignación del trabajador.
   *
   * @param assignments Número de asignaciones que se realizarán.
   * @param gatherer Actor usado para acumular los resultados.
   */
  case class Assign(assignments: Int, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la inhabilitación de un trabajador.
   *
   * @param workerID Identificador del trabajador.
   * @param gatherer Actor usado para acumular los resultados.
   */
  case class Disable(workerID: String, selfEnablingDate: DateTime, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la auto-habilitación del trabajador.
   */
  case object SelfEnable extends Command

  /**
   * Comando que inicia la habilitación de un trabajador.
   *
   * @param workerID Identificador del trabajador.
   * @param gatherer Actor usado para acumular los resultados.
   */
  case class Enable(workerID: String, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia el intento de eliminación del trabajador.
   *
   * @param workerID Identificador del trabajador.
   * @param gatherer Actor usado para acumular los resultados.
   */
  case class TryDelete(workerID: String, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la verificación de la validez del trabajador para ser transferido.
   *
   * @param workerID Identificador del trabajador.
   * @param officeCode Código de la of del trabajador.
   * @param gatherer Actor usado para acumular los resultados.
   */
  case class CheckTransfer(workerID: String, officeCode: String, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la transferencia del trabajador a la oficina dada.
   */
  case class Transfer(officeCode: String, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la verificación de la existencia de los trabajadores que transferirán las solicitudes entre si.
   */
  case class CheckTransferAssignments(originWorkerID: String, destinationWorkerID: String, assignments: Int, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la asignación del trabajador sin revisar si esta activado o no.
   */
  case class TransferAssignments(assignments: Int, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la remoción de solicitudes dadas del trabajador dado.
   */
  case class TryUnassign(workerID: String, assignments: Int, gatherer: ActorRef) extends Command

  /**
   * Comando que indica el cambio de role de el trabajador
   */
  case class ChangeRoleMsg(workerID: String, workerRole: String) extends Command

  /**
   * Comando que verifica si el trabajador existe.
   */
  case class CheckWorkerExist(workerID: String, gatherer: ActorRef) extends Command

  /**
   * Evento indica se definió la información inicial del trabajador como el código de la oficina a la que pertenece y el
   * máximo número de asignaciones que puede recibir.
   */
  case class WorkerInfoDefined(officeCode: String, maxAssignments: Int, gatherer: ActorRef, role: String) extends Event

  /**
   * Evento indica se actualizó máximo número de asignaciones del trabajador.
   */
  case class MaxAssignmentsUpdated(maxAssignments: Int) extends Event

  /**
   * Evento indica se realizo una asignación en el trabajador.
   *
   * @param assignments Número de asignaciones que se realizaron.
   * @param gatherer Actor usado para acumular los resultados.
   */
  case class Assigned(assignments: Int, gatherer: ActorRef) extends Event

  /**
   * Evento indica se in-habilitó el trabajador.
   */
  case class Disabled(selfEnablingDate: DateTime, gatherer: ActorRef) extends Event

  /**
   * Evento indica se auto-habilito el trabajador.
   */
  case class SelfEnabled() extends Event

  /**
   * Evento indica se habilito el trabajador.
   */
  case class Enabled(gatherer: ActorRef) extends Event

  /**
   * Evento indica se elimino el trabajador.
   */
  case class Deleted(gatherer: ActorRef) extends Event

  /**
   * Evento indica se transfirió el trabajador.
   */
  case class Transferred(officeCode: String, gatherer: ActorRef) extends Event

  /**
   * Evento indica se realizo una transferencia del o hacia el trabajador.
   */
  case class AssignmentsTransferred(assignments: Int, gatherer: ActorRef) extends Event

  /**
   * Evento indica se realizo una remoción de solicitudes del trabajador.
   */
  case class Unassigned(assignments: Int, gatherer: ActorRef) extends Event

  /**
   * Evento que indica un cambio de rol de el rabajador
   */
  case class ChangeRole(role: String, gatherer: ActorRef) extends Event

}

/**
 * Un trabajador del sistema.
 *
 * @param ID Identificador del trabajador.
 */
case class Worker(ID: String) extends ActorWithPersistence[WorkerState] {

  /**
   * Estado actual del trabajador.
   */
  var state: WorkerState = WorkerState()

  // scalastyle:off cyclomatic.complexity
  /**
   * Handler de eventos que se persistieron exitosamente y se han recuperado.
   *
   * @param event Evento persistido recuperado.
   */
  override def handleRecoveredEvent(event: Event): Unit = event match {
    case WorkerInfoDefined(code, max, _, role) =>
      updateOfficeCode(code)
      updateMaxAssignments(max)
      updateRole(role)

    case MaxAssignmentsUpdated(max) => updateMaxAssignments(max)
    case Assigned(assignments, _) => updateAssignments(assignments)

    case Disabled(selfEnablingDate, _) =>
      disable()
      updateSelfEnablingDate(Some(selfEnablingDate))

    case SelfEnabled() =>
      enable()
      updateSelfEnablingDate(None)

    case Enabled(_) =>
      enable()
      updateSelfEnablingDate(None)

    case Deleted(_) => state = WorkerState()

    case Transferred(code, _) => updateOfficeCode(code)
    case AssignmentsTransferred(assignments, _) => updateAssignments(assignments)
    case Unassigned(assignments, _) => updateAssignments(0 - assignments)
    case msg: Any => printUnknownRecovery(msg)
  }
  // scalastyle:on cyclomatic.complexity

  /**
   * Función llamada una vez se haya completado la recuperación del actor de forma exitosa.
   */
  override def recoveryCompleted(): Unit = {
    // la primera vez que se crea el trabajador, no tiene oficina, por los que no tendrá eventos tampoco.
    if (state.officeCode.nonEmpty) {
      if (!state.enabled) schedulerSelfEnabling()
      printState()
      updateOfficeAssignments()
    }
  }

  private def process(msg: CheckWorkerExist) = if (msg.workerID.equals(ID)) msg.gatherer ! WorkerExists else msg.gatherer ! NotMe

  // scalastyle:off cyclomatic.complexity
  /**
   * Maneja los mensajes recibidos por el trabajador.
   */
  override def receive: Receive = {
    case DefineWorkerInfo(code, max, role) => persistEvent(WorkerInfoDefined(code, max, sender(), role))
    case msg: UpdateMaxAssignments => process(msg)
    case msg: Assign => process(msg)
    case msg: Disable => process(msg)
    case SelfEnable => persistEvent(SelfEnabled())
    case msg: Enable => process(msg)
    case msg: TryDelete => process(msg)
    case msg: CheckTransfer => process(msg)
    case Transfer(officeCode, gatherer) => persistEvent(Transferred(officeCode, gatherer))
    case msg: CheckTransferAssignments => process(msg)
    case TransferAssignments(assignments, gatherer) => persistEvent(AssignmentsTransferred(assignments, gatherer))
    case msg: TryUnassign => process(msg)
    case SaveActorState => trySaveState(state)
    case msg: DeadLetter => reviewDeadLetter(msg)
    case msg: ChangeRoleMsg => process(msg, sender())
    case msg: CheckWorkerExist => process(msg)
    case msg => printUnknownMsg(msg)
  }
  // scalastyle:on cyclomatic.complexity

  // scalastyle:off method.length
  // scalastyle:off cyclomatic.complexity
  /**
   * Handler de eventos que se acaban de enviar a persistir.
   *
   * @param event Evento enviado a persistir.
   */
  def handlePersistedEvent(event: Event): Unit = event match {
    case WorkerInfoDefined(code, max, gatherer, role) =>
      updateOfficeCode(code)
      updateMaxAssignments(max)
      updateRole(role)
      gatherer ! WorkerSupervisedCreatedSuccessfully

    case MaxAssignmentsUpdated(max) =>
      updateMaxAssignments(max)
      updateOfficeAssignments()

    case Assigned(assignments, gatherer) =>
      updateAssignments(assignments)
      printState()
      updateOfficeAssignments()
      sendToMyOffice(BuildWorkerAssigned(ID, state.role, state.maxAssignments, gatherer))

    case Disabled(selfEnablingDate, gatherer) =>
      disable()
      updateSelfEnablingDate(Some(selfEnablingDate))
      schedulerSelfEnabling()
      printState()
      gatherer ! WorkerSuccessfullyDisabled

    case SelfEnabled() =>
      enable()
      updateSelfEnablingDate(None)
      updateSchedulerSelfEnabling(None)
      printState()

    case Enabled(gatherer) =>
      enable()
      disableSchedulerSelfEnabling()
      printState()
      gatherer ! WorkerSuccessfullyEnabled

    case Deleted(gatherer) =>
      gatherer ! WorkerDeleted(state.officeCode.get, state.role)
      delete()

    case Transferred(officeCode, gatherer) =>
      updateOfficeCode(officeCode)
      printState()
      updateOfficeAssignments()
      gatherer ! WorkerSuccessfullyTransferred

    case AssignmentsTransferred(assignments, gatherer) =>
      updateAssignments(assignments)
      printState()
      updateOfficeAssignments()
      gatherer ! AssignmentsSuccessfullyTransferred

    case Unassigned(assignments, gatherer) =>
      updateAssignments(0 - assignments)
      printState()
      updateOfficeAssignments()
      gatherer ! AssignmentsSuccessfullyUnassigned

    case ChangeRole(role, gatherer) =>
      val oldRole = state.role
      updateRole(role)
      printState()
      updateOfficeRoleWorker(oldRole)
      gatherer ! ChangeRoleSuccessfully

    case msg: Any => printUnknownPersisted(msg)
  }
  // scalastyle:on cyclomatic.complexity
  // scalastyle:on method.length

  /**
   * Procesa el mensaje [[UpdateMaxAssignments]].
   */
  private def process(msg: UpdateMaxAssignments) =
    if (state.role.equals(msg.workerRole)) persistEvent(MaxAssignmentsUpdated(msg.maxAssignments))

  /**
   * Procesa el mensaje [[Assign]].
   */
  private def process(msg: Assign) = {
    if (state.enabled) {
      val event = Assigned(msg.assignments, msg.gatherer)
      persistEvent(event)
    } else {
      msg.gatherer ! WorkerCannotBeAssigned
    }
  }

  /**
   * Procesa el mensaje [[Disable]].
   */
  private def process(msg: Disable) = {
    if (msg.workerID.equals(ID)) {
      if (state.assignments == 0) {
        if (state.enabled) {
          persistEvent(Disabled(msg.selfEnablingDate, msg.gatherer))
        } else {
          msg.gatherer ! WorkerAlreadyDisabled
        }
      } else {
        msg.gatherer ! WorkerHasAssignedTasks(state.assignments)
      }
    } else {
      msg.gatherer ! NotMe
    }
  }

  /**
   * Procesa el mensaje [[Enable]].
   */
  private def process(msg: Enable) = {
    if (msg.workerID.equals(ID)) {
      if (!state.enabled) {
        persistEvent(Enabled(msg.gatherer))
      } else {
        msg.gatherer ! WorkerAlreadyEnabled
      }
    } else {
      msg.gatherer ! NotMe
    }
  }

  /**
   * Procesa el mensaje [[TryDelete]].
   */
  private def process(msg: TryDelete) = {
    if (msg.workerID.equals(ID)) {
      if (state.assignments == 0) {
        persistEvent(Deleted(msg.gatherer))
      } else {
        msg.gatherer ! WorkerHasAssignedTasks(state.assignments)
      }
    } else {
      msg.gatherer ! NotMe
    }
  }

  /**
   * Procesa el mensaje [[CheckTransfer]].
   */
  private def process(msg: CheckTransfer) = {
    if (msg.workerID.equals(ID)) {
      if (msg.officeCode.equals(state.officeCode.get)) {
        msg.gatherer ! WorkerAlreadyThere
      } else {
        msg.gatherer ! WorkerTransfer(state.officeCode.get, state.role, state.maxAssignments)
      }
    } else {
      msg.gatherer ! NotMe
    }
  }

  /**
   * Procesa el mensaje [[CheckTransferAssignments]].
   */
  private def process(msg: CheckTransferAssignments) = {
    if (ID.equals(msg.originWorkerID)) {
      if (state.assignments < msg.assignments) {
        msg.gatherer ! WorkerNotEnoughAssignments(state.assignments)
      } else {
        msg.gatherer ! WorkerTransferAssignments(ID, state.role)
      }
    } else if (ID.equals(msg.destinationWorkerID)) {
      val assignments = state.assignments + msg.assignments
      if (assignments > state.maxAssignments) {
        val diff = state.maxAssignments - state.assignments
        msg.gatherer ! AssignmentsWorkerDestinationOverLimit(diff)
      } else {
        msg.gatherer ! WorkerTransferAssignments(ID, state.role)
      }
    } else {
      msg.gatherer ! NotMe
    }
  }

  /**
   * Procesa el mensaje [[TryUnassign]].
   */
  private def process(msg: TryUnassign) = {
    if (ID.equals(msg.workerID)) {
      if (state.assignments < msg.assignments) {
        msg.gatherer ! WorkerNotEnoughAssignments(state.assignments)
      } else {
        persistEvent(Unassigned(msg.assignments, msg.gatherer))
      }
    } else {
      msg.gatherer ! NotMe
    }
  }

  /**
   * Procesa el mensaje [[ChangeRoleMsg]].
   */
  private def process(msg: ChangeRoleMsg, gatherer: ActorRef): Unit = {
    if (!state.role.equals(msg.workerRole)) {
      persistEvent(ChangeRole(msg.workerRole, gatherer))
    }
  }

  /**
   * Programa la activación del trabajador después de los dias indicados por el estado del mismo.
   */
  private def schedulerSelfEnabling() = {
    val wait = state.selfEnablingDate.get.dayOfYear().get() - DateTime.now().dayOfYear().get()
    val cancellable = state.selfEnablingDate.map(_ => SystemActors.system.scheduler.scheduleOnce(wait.days, self, SelfEnable))
    updateSchedulerSelfEnabling(cancellable)
  }

  /**
   * Detiene la programación de la activación del trabajador.
   */
  private def disableSchedulerSelfEnabling() = {
    state.schedulerSelfEnabling.foreach(_.cancel())
    updateSelfEnablingDate(None)
    updateSchedulerSelfEnabling(None)
  }

  /**
   * Envía las asignaciones del trabajador para que la oficina se actualice.
   */
  private def updateOfficeAssignments() = sendToMyOffice(UpdateWorkerAssignments(ID, state.role, state.maxAssignments, state.assignments))

  /**
   * Envía el nuevo rol de el trabajador para que lo actualice.
   */
  private def updateOfficeRoleWorker(oldrole: String) = sendToMyOffice(UpdateWorkerRole(ID, state.role, oldrole, state.maxAssignments))

  /**
   * Envía el mensaje dado a la oficina a la que pertenece el trabajador.
   */
  private def sendToMyOffice(msg: Any) = sendToOffice(msg, state.officeCode.get)

  /**
   * Habilita el trabajador
   */
  private def enable() = state = state.copy(enabled = true)

  /**
   * Define la fecha de auto-habilitación.
   */
  private def updateSelfEnablingDate(selfEnablingDate: Option[DateTime]) = state = state.copy(selfEnablingDate = selfEnablingDate)

  /**
   * Define la fecha de auto-habilitación.
   */
  private def updateSchedulerSelfEnabling(schedulerSelfEnabling: Option[Cancellable]) = state = state.copy(schedulerSelfEnabling = schedulerSelfEnabling)

  /**
   * In-habilita el trabajador.
   */
  private def disable() = state = state.copy(enabled = false)

  /**
   * Asigna las tarea del trabajador.
   */
  private def updateAssignments(assignments: Int) = state = state.copy(assignments = state.assignments + assignments)

  /**
   * Define el código de la oficina a la que pertenece el trabajador.
   */
  private def updateOfficeCode(officeCode: String) = state = state.copy(officeCode = Some(officeCode))

  /**
   * Actualiza el máximo número de tareas que puede recibir el trabajador.
   */
  private def updateMaxAssignments(maxAssignments: Int) = state = state.copy(maxAssignments = maxAssignments)

  /**
   * Actualiza el role del trabajador
   * @param role rol a definir en el trabajador
   */
  private def updateRole(role: String) = state = state.copy(role = role)
}
