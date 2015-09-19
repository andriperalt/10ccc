package co.com.sura.distribucion.asignacion.node.offices.aggregate

import akka.actor.{ ActorRef, DeadLetter, Props }

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.ActorWithPersistence.{ ActorWithPersistenceState, SaveActorState }
import co.com.sura.distribucion.asignacion.node.commons.aggregate._
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.offices.aggregate.Office._
import co.com.sura.distribucion.asignacion.node.regionals.aggregate.Regional.{ BuildOfficeAssigned, GiveRegionalInfo, RegionalRoleData, UpdateOfficeInfo }
import co.com.sura.distribucion.asignacion.node.roles.aggregate.RolesInfo.GiveMaxAssignmentsByRole
import co.com.sura.distribucion.asignacion.node.workers.aggregate.WorkersSupervisor.CreateWorkerRecovered

/**
 * Fábrica de instancias de [[Office]]
 * Define los mensajes específicos procesados en última instancia por una oficina.
 */
object Office {

  /**
   * Retorna Props del actor [[Office]].
   */
  def props(regionalCode: String, name: String, code: String): Props = Props(Office(regionalCode, name, code))

  /**
   * Estado de la oficina.
   *
   * @param maxAssignments Máximo número de asignaciones totales que se pueden recibir en la oficina.
   * @param assignments Número de asignaciones totales que se han recibido en la oficina.
   * @param occupancyPercentage Porcentaje de ocupación de la oficina.
   * @param numWorkers Número de trabajadores totales en la oficina.
   * @param roles Tabla con la información de los roles de los trabajadores registrados en la oficina.
   */
  case class OfficeState(
      maxAssignments: Int = 0, assignments: Int = 0, occupancyPercentage: Double = 0.0, numWorkers: Int = 0,
      roles: Map[String, OfficeRoleData] = Map.empty[String, OfficeRoleData]
  ) extends ActorWithPersistenceState {

    override def toString: String = {
      val builder = new StringBuilder()
        .append(s"Maximo numero de asignaciones: $maxAssignments\n")
        .append(s"Numero de asignaciones actuales: $assignments\n")
        .append(s"Porcentaje de ocupación actual: $occupancyPercentage%\n")
        .append(s"Numero de trabajadores actuales: $numWorkers\n")
        .append("Roles:\n")
      roles foreach { e =>
        val (roleName, roleData) = e
        builder.append(s"\tRol: $roleName\n")
          .append(s"\tDatos:\n")
          .append(s"\t$roleData\n")
      }
      builder.toString()
    }
  }

  /**
   * Información de los roles de los trabajadores registrados en la oficina.
   *
   * @param maxAssignments Máximo número de asignaciones totales que pueden recibir los trabajadores con el rol.
   * @param assignments Número de asignaciones totales actuales que han recibido los trabajadore con el rol.
   * @param workers Tabla con la información de los trabajadores de la oficina con el rol.
   */
  private[Office] case class OfficeRoleData(
      maxAssignments: Int = 0, assignments: Int = 0, workers: Map[String, OfficeWorkerData] = Map.empty[String, OfficeWorkerData]
  ) {

    override def toString: String = {
      val builder = new StringBuilder()
        .append(s"\tMaximo numero de asignaciones: $maxAssignments\n")
        .append(s"\t\tNumero de asignaciones actuales: $assignments\n")
        .append(s"\t\tTrabajadores:\n")
      workers foreach { e =>
        val (workerID, workerData) = e
        builder.append(s"\t\t\tTrabajador: $workerID\n")
          .append(s"\t\t\tDatos:\n")
          .append(s"\t\t\t$workerData\n")
      }
      builder.toString()
    }
  }

  /**
   * Información del trabajador que se guarda en la tabla de trabajadores de la oficina.
   *
   * @param maxAssignments Máximo número de asignaciones que puede recibir el trabajador.
   * @param assignments Número de asignaciones actuales del trabajador.
   * @param role Rol del trabajador.
   */
  private[Office] case class OfficeWorkerData(maxAssignments: Int = 0, assignments: Int = 0, role: String) {

    override def toString: String = {
      val builder = new StringBuilder()
        .append(s"\tMaximo numero de asignaciones: $maxAssignments\n")
        .append(s"\t\t\t\tNumero de asignaciones actuales: $assignments\n")
        .append(s"\t\t\t\tRol: $role\n")
      builder.toString()
    }
  }

  /**
   * Comando que inicia la actualización de las asignaciones del trabajador dado.
   */
  case class UpdateWorkerAssignments(workerID: String, workerRole: String, workerMaxAssignments: Int, workerAssignments: Int) extends Command

  /**
   * Comando que inicia la actualización del rol del trabajador dado.
   */
  case class UpdateWorkerRole(workerID: String, workerRole: String, oldRole: String, workerMaxAssignments: Int) extends Command

  /**
   * Comando que inicia la creación de un trabajador en la oficina.
   */
  case class CreateWorker(workerID: String, workerRole: String, workerMaxAssignments: Int) extends Command

  /**
   * Comando que inicia la verificación de la existencia de una oficina.
   *
   * @param officeCode Identificador de la oficina a buscar.
   * @param gatherer Actor usado para acumular los resultados del intento de verificación.
   */
  case class CheckOfficeExists(officeCode: String, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la recolección de los trabajadores con el rol dado y con la capacidad para recibir las
   * asignaciones dadas de una oficina.
   *
   * @param officeCode Código de la oficina.
   * @param role Rol de los trabajadores a los que se busca asignar.
   * @param assignments Número de asignaciones que afectará la capacidad.
   * @param gatherer Actor al que se le responderá.
   */
  case class GiveWorkersWithCapacity(officeCode: String, role: String, assignments: Int, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la recolección de la información para el servicio de asignación del trabajador asignado.
   */
  case class BuildWorkerAssigned(workerID: String, workerRole: String, workerMaxAssignments: Int, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la eliminación de un trabajador en la oficina.
   */
  case class DeleteWorker(workerID: String, workerRole: String) extends Command

  /**
   * Comando que inicia la transferencia de un trabajador a otra oficina.
   */
  case class TransferWorker(workerID: String, officeCode: String, gatherer: ActorRef) extends Command

  /**
   * Comando que inicia la recolección de la información de la oficina.
   */
  case class GiveOfficeInfo(gatherer: ActorRef) extends Command

  /**
   * Evento que indica se creó un trabajador en la oficina.
   *
   * @param workerID Identificador del trabajador.
   * @param workerRole Rol del trabajador.
   * @param gatherer Actor usado para acumular los resultados del intento de creación.
   */
  case class WorkerCreated(workerID: String, workerRole: String, workerMaxAssignments: Int, gatherer: ActorRef) extends Event

  /**
   * Evento que indica se eliminó un trabajador en la oficina.
   */
  case class WorkerDeleted(workerID: String, workerRole: String, gatherer: ActorRef) extends Event

}

/**
 * Una oficina que pertenece a una regional.
 *
 * @param regionalCode Código de la regional a la que pertenece la oficina.
 * @param name Nombre de la oficina.
 * @param code Código de la oficina.
 */
case class Office(regionalCode: String, name: String, code: String) extends ActorWithPersistence[OfficeState] {

  /**
   * Estado actual de la oficina.
   */
  var state: OfficeState = OfficeState()

  /**
   * Override para detener el sistema cuando se falla en recuperar una oficina, que solo puede pasar en el inicio.
   */
  override def postStop(): Unit = sys.exit()

  /**
   * Handler de eventos que se persistieron exitosamente y se han recuperado.
   *
   * @param evt Evento persistido recuperado.
   */
  def handleRecoveredEvent(evt: Event): Unit = evt match {
    case evt: WorkerCreated => process(evt)
    case evt: WorkerDeleted => process(evt)
    case msg => printUnknownMsg(msg)
  }

  /**
   * Función llamada una vez se haya completado la recuperación de la oficina de forma exitosa.
   */
  def recoveryCompleted(): Unit = {
    // Reiniciar asignaciones para obtenerlas de los trabajadores directamente cuando se recuperen
    updateMaxAssignments(0 - state.maxAssignments)
    updateAssignments(0 - state.assignments)
    resetRoleAssignments(state.roles)
    sendToRolesInfo(GiveMaxAssignmentsByRole)
  }

  // scalastyle:off cyclomatic.complexity
  /**
   * Maneja los mensajes recibidos por la oficina.
   */
  def receive: Receive = {
    case msg: MaxAssignmentsByRole => process(msg)
    case msg: UpdateWorkerAssignments => process(msg)
    case msg: CheckOfficeExists => process(msg)
    case CreateWorker(id, role, max) => persistEvent(WorkerCreated(id, role, max, sender()))
    case msg: GiveWorkersWithCapacity => process(msg)
    case DeleteWorker(workerID, workerRole) => persistEvent(WorkerDeleted(workerID, workerRole, sender()))
    case msg: BuildWorkerAssigned => process(msg)
    case msg: GiveOfficeInfo => process(msg)
    case SaveActorState => trySaveState(state)
    case msg: DeadLetter => reviewDeadLetter(msg)
    case msg: UpdateWorkerRole => updateWorkerRole(msg)
    case msg => printUnknownMsg(msg)
  }
  // scalastyle:on cyclomatic.complexity

  /**
   * Handler de eventos que se acaban de enviar a persistir.
   *
   * @param event Evento enviado a persistir.
   */
  def handlePersistedEvent(event: Event): Unit = event match {
    case event: WorkerCreated =>
      process(event)
      printState()
      updateInfoRegional()
      event.gatherer ! WorkerCreatedSuccessfully

    case event: WorkerDeleted =>
      process(event)
      printState()
      updateInfoRegional()
      event.gatherer ! WorkerDeletedSuccessfully

    case msg => printUnknownPersisted(msg)
  }

  /**
   * Procesa el evento [[WorkerCreated]]
   *
   * Retorna el máximo número de asignaciones pare el rol del trabajador creado.
   */
  private def process(evt: WorkerCreated) = {
    val roleData = state.roles.getOrElse(evt.workerRole, OfficeRoleData())
    val roleDataUpd = roleData.copy(maxAssignments = roleData.maxAssignments + evt.workerMaxAssignments)
    val workerData = OfficeWorkerData(role = evt.workerRole, maxAssignments = evt.workerMaxAssignments)
    addWorker(roleDataUpd, evt.workerID, workerData)
    updateMaxAssignments(evt.workerMaxAssignments)
  }

  /**
   * Procesa el evento [[WorkerDeleted]]
   */
  private def process(evt: WorkerDeleted) = {
    val roleData = state.roles(evt.workerRole)
    val workerData = state.roles(evt.workerRole).workers(evt.workerID)
    updateWorkerAssignments(0, 0, evt.workerRole, roleData, evt.workerID, workerData)
    val roleDataUpd = state.roles(evt.workerRole)
    delWorker(evt.workerRole, roleDataUpd, evt.workerID)
  }

  /**
   * Procesa el mensaje [[MaxAssignmentsByRole]].
   */
  private def process(msg: MaxAssignmentsByRole) = {
    var i = 0
    var rolesUpd = Map.empty[String, OfficeRoleData]
    state.roles.keys foreach { roleName =>
      val roleData = state.roles(roleName)
      val (roleNameUpd, roleDataUpd) = msg.roles get roleName match {
        case Some(x) => (roleName, roleData)
        case None =>
          i = i + 1
          val roleNameUpd = s"N/A$i"
          (roleNameUpd, changeWorkersRoleName(roleNameUpd, roleData))
      }
      rolesUpd = rolesUpd + (roleNameUpd -> roleDataUpd)
    }
    state = state.copy(roles = rolesUpd)
    printState()
    state.roles.values foreach recoverRoleWorkers
  }

  /**
   * Procesa el mensaje [[UpdateWorkerAssignments]].
   */
  private def process(msg: UpdateWorkerAssignments) = {
    val roleData = state.roles(msg.workerRole)
    val workerData = roleData.workers(msg.workerID)
    updateWorkerAssignments(msg.workerMaxAssignments, msg.workerAssignments, msg.workerRole, roleData, msg.workerID, workerData)
    printState()
    updateInfoRegional()
  }

  /**
   * Procesa el mensaje [[CheckOfficeExists]].
   */
  private def process(msg: CheckOfficeExists) = {
    val rsp = if (msg.officeCode.equals(code)) OfficeExists else NotMe
    msg.gatherer ! rsp
  }

  /**
   * Procesa el mensaje [[GiveWorkersWithCapacity]].
   */
  private def process(msg: GiveWorkersWithCapacity) = {
    if (msg.officeCode.equals(code)) {
      giveWorkersWithCapacity(msg.role, msg.assignments, msg.gatherer)
    } else {
      msg.gatherer ! NotMe
    }
  }

  /**
   * Procesa el mensaje [[BuildWorkerAssigned]].
   */
  private def process(msg: BuildWorkerAssigned) = {
    val toMsg = BuildOfficeAssigned(msg.workerID, msg.workerMaxAssignments, code, name, state.maxAssignments, msg.gatherer)
    sendToMyRegional(toMsg)
  }

  /**
   * Procesa el mensaje [[GiveOfficeInfo]].
   */
  private def process(msg: GiveOfficeInfo) = {
    var roles = Seq.empty[InfoRoleOficina]
    state.roles.keys.foreach { roleName =>
      val roleData = state.roles(roleName)
      if (roleData.workers.nonEmpty) {
        val roleWorkersInfo = roleData.workers.toList.map(
          x => InfoPersona(x._1, x._2.role, x._2.assignments)
        ).sortWith(_.login < _.login)
        roles = roles :+ InfoRoleOficina(roleName, roleWorkersInfo.size, roleWorkersInfo)
      }
    }
    roles = roles.sortWith(_.nombre < _.nombre)
    val infoOffice = InfoOficina(code, name, state.maxAssignments, state.assignments, s"${state.occupancyPercentage}%", state.numWorkers, roles)
    sendToMyRegional(GiveRegionalInfo(infoOffice, msg.gatherer))
  }

  /**
   * Reinicia las asignaciones de los roles y los trabajadores.
   */
  private def resetRoleAssignments(roles: Map[String, OfficeRoleData]) = {
    var rolesUpd = Map.empty[String, OfficeRoleData]
    roles.keys.foreach { roleName =>
      val roleData = roles(roleName)
      val workersUpd = resetWorkerAssignments(roleData)
      rolesUpd = rolesUpd + (roleName -> roleData.copy(maxAssignments = 0, assignments = 0, workers = workersUpd))
    }
    state = state.copy(roles = rolesUpd)
  }

  /**
   * Reinicia las asignaciones de los trabajadores.
   */
  private def resetWorkerAssignments(roleData: OfficeRoleData) = {
    var workers = Map.empty[String, OfficeWorkerData]
    roleData.workers.keys foreach { workerID =>
      workers = workers + (workerID -> roleData.workers(workerID).copy(maxAssignments = 0, assignments = 0))
    }
    workers
  }

  /**
   * Cambia el nombre del rol  de los trabajadores por el dado.
   */
  private def changeWorkersRoleName(roleName: String, roleData: OfficeRoleData) =
    roleData.copy(workers = roleData.workers.mapValues(_.copy(role = roleName)))

  /**
   * Inicia la recuperación de la lista de trabajadores con rol dado.
   */
  private def recoverRoleWorkers(roleData: OfficeRoleData) = roleData.workers.keys foreach { workerID =>
    sendToWorkersSupervisor(CreateWorkerRecovered(code, workerID, roleData.workers(workerID).role))
  }

  /**
   * Actualiza el número de asignaciones del trabajador, del rol y de las oficinas.
   */
  private def updateWorkerAssignments(
    maxAssignments: Int, assignments: Int, roleName: String, roleData: OfficeRoleData, workerID: String, workerData: OfficeWorkerData
  ) = {
    val newWorkerMaxAssignments = maxAssignments - workerData.maxAssignments
    val newWorkerAssignments = assignments - workerData.assignments
    val roleDataUpd = roleData.copy(
      maxAssignments = roleData.maxAssignments + newWorkerMaxAssignments,
      assignments = roleData.assignments + newWorkerAssignments
    )
    addUpdateWorker(roleDataUpd, workerID, workerData.copy(maxAssignments = maxAssignments, assignments = assignments))
    updateMaxAssignments(newWorkerMaxAssignments)
    updateAssignments(newWorkerAssignments)
  }

  /**
   * Busca y envía los trabajadores que tienen capacidad para recibir las asignaciones dadas.
   */
  private def giveWorkersWithCapacity(workerRole: String, assignments: Int, gatherer: ActorRef): Unit = {
    var workers = Map.empty[String, Int]
    val officeAssignments = state.assignments + assignments
    if (officeAssignments <= state.maxAssignments) {
      if (state.roles.get(workerRole).nonEmpty) {
        workers = collectWorkersWithCapacity(assignments, state.roles(workerRole))
      }
    }
    if (workers.isEmpty) gatherer ! NotWorkersWithCapacity(regionalCode) else gatherer ! WorkersWithCapacity(regionalCode, code, workers)
  }

  /**
   * Busca los trabajadores que tienen capacidad para recibir las asignaciones dadas en el rol dado.
   */
  private def collectWorkersWithCapacity(assignments: Int, roleData: OfficeRoleData): Map[String, Int] = {
    var workers = Map.empty[String, Int]
    val roleAssignments = roleData.assignments + assignments
    if (roleAssignments <= roleData.maxAssignments) roleData.workers.keys foreach { workerID =>
      val workerData = roleData.workers(workerID)
      val workerAssignments = workerData.assignments + assignments
      if (workerAssignments <= roleData.maxAssignments) workers = workers + (workerID -> workerData.assignments)
    }
    workers
  }

  /**
   * Agrega el trabajador en la tabla de trabajadores del rol dado.
   */
  private def addWorker(roleData: OfficeRoleData, workerID: String, workerData: OfficeWorkerData) = {
    state = state.copy(numWorkers = state.numWorkers + 1)
    addUpdateRole(workerData.role, roleData.copy(workers = roleData.workers + (workerID -> workerData)))
  }

  /**
   * Elimina el trabajador de la tabla de trabajadores del rol dado.
   */
  private def delWorker(roleName: String, roleData: OfficeRoleData, workerID: String) = {
    state = state.copy(numWorkers = state.numWorkers - 1)
    addUpdateRole(roleName, roleData.copy(workers = roleData.workers - workerID))
    if (state.roles(roleName).workers.isEmpty) state = state.copy(roles = state.roles - roleName)
  }

  /**
   * Agrega o actualiza el trabajador en la tabla de trabajadores del rol dado.
   */
  private def addUpdateWorker(roleData: OfficeRoleData, workerID: String, workerData: OfficeWorkerData) = {
    addUpdateRole(workerData.role, roleData.copy(workers = roleData.workers + (workerID -> workerData)))
  }

  /**
   * Agrega o actualiza el rol a la tabla de roles de la oficina.
   */
  private def addUpdateRole(roleName: String, roleData: OfficeRoleData) = {
    state = state.copy(roles = state.roles + (roleName -> roleData))
  }

  /**
   * Actualiza el máximo número de asignaciones de la oficina.
   */
  private def updateMaxAssignments(maxAssignments: Int) = {
    state = state.copy(maxAssignments = state.maxAssignments + maxAssignments)
    updateOccupancyPercentage()
  }

  /**
   * Actualiza el máximo número de asignaciones de la oficina.
   */
  private def updateAssignments(assignments: Int) = {
    state = state.copy(assignments = state.assignments + assignments)
    updateOccupancyPercentage()
  }

  /**
   * Actualiza el porcentaje de ocupación de la oficina.
   */
  private def updateOccupancyPercentage() = {
    val occupancyPercentage = if (state.maxAssignments > 0) {
      val percentage = (state.assignments.toDouble / state.maxAssignments.toDouble) * 100.0
      BigDecimal(percentage).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
    } else {
      0.0
    }
    state = state.copy(occupancyPercentage = occupancyPercentage)
  }

  /**
   * Envía la información de esta oficina a su sucursal para que ésta se actualize.
   */
  private def updateInfoRegional() = {
    var roles = Map.empty[String, RegionalRoleData]
    state.roles.keys foreach { roleName =>
      val roleData = state.roles(roleName)
      val regionalRoleData = RegionalRoleData(
        maxAssignments = roleData.maxAssignments, assignments = roleData.assignments
      )
      roles = roles + (roleName -> regionalRoleData)
    }
    sendToMyRegional(UpdateOfficeInfo(code, state.maxAssignments, state.assignments, roles))
  }

  private def updateWorkerRole(msg: UpdateWorkerRole) {
    state.roles.get(msg.oldRole).foreach { role =>
      role.workers.get(msg.workerID).foreach { worker =>
        val workersMap = role.workers - msg.workerID
        val assignments = state.roles(msg.oldRole).assignments - worker.assignments
        val roleData = state.roles(msg.oldRole).copy(assignments, state.roles(msg.oldRole).maxAssignments, workersMap)
        state = state.copy(roles = state.roles + (msg.oldRole -> roleData))
        if (state.roles.keys.find(_ == msg.workerRole).getOrElse(false) == msg.workerRole) {
          val newWorkerMap = state.roles(msg.workerRole).workers + (msg.workerID -> worker)
          val newRoleWithWorker = state.roles(msg.workerRole).copy(
            state.roles(msg.workerRole).assignments + worker.assignments, state.roles(msg.workerRole).maxAssignments, newWorkerMap
          )
          state = state.copy(roles = state.roles + (msg.workerRole -> newRoleWithWorker))
        } else {
          val workersNew = Map.empty[String, OfficeWorkerData]
          val mapWorkers = workersNew + (msg.workerID -> OfficeWorkerData(msg.workerMaxAssignments, worker.assignments, msg.workerRole))
          val roleDataNew = OfficeRoleData(msg.workerMaxAssignments, worker.assignments, mapWorkers)
          state = state.copy(
            state.maxAssignments, state.assignments, state.occupancyPercentage, state.numWorkers, state.roles + (msg.workerRole -> roleDataNew)
          )
        }
      }
    }
  }

  /**
   * Envía el mensaje dado a la regional a la que pertenece la oficina
   */
  private def sendToMyRegional(msg: Any) = sendToRegional(msg, regionalCode)
}
