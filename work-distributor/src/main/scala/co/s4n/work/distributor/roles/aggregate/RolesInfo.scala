package co.com.sura.distribucion.asignacion.node.roles.aggregate

import akka.actor.{ ActorRef, DeadLetter }
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection.mutable

import co.com.sura.distribucion.asignacion.node.commons.constants.{ Constants, ErrorsConstants, LogConstants }
import co.com.sura.distribucion.asignacion.node.commons.exceptions.{ AssignmentAlgorithmNotExist, ElementRepeatedException }
import co.com.sura.distribucion.asignacion.node.commons.util.StringUtils._
import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.ActorWithPersistence.{ ActorWithPersistenceState, SaveActorState }
import co.com.sura.distribucion.asignacion.node.commons.aggregate._
import co.com.sura.distribucion.asignacion.node.commons.ws.{ ProcessNotAssociatedWithRole, MaxAssignmentsModifiedSuccessfully, RoleUndefined, Request }
import co.com.sura.distribucion.asignacion.node.gatherer.aggregate.GathererBuilder.AssignGathererFounded
import co.com.sura.distribucion.asignacion.node.roles.aggregate.RolesInfo._
import co.com.sura.distribucion.asignacion.node.workers.aggregate.Worker.UpdateMaxAssignments
import co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers.assign._

/**
 * Define los mensajes específicos procesados en última instancia por el actor que maneja los roles de los trabajadores.
 */
object RolesInfo {

  /**
   * Tabla de algoritmos de asignación.
   */
  private[RolesInfo] val assignAlgorithms: Map[AssignAlgorithmsKey, GathererObject] = Map(
    AssignAlgorithmsKey("AUXILIAR", "0007") -> SearchAllRed,
    AssignAlgorithmsKey("APRENDIZ", "0007") -> SearchAllRed,
    AssignAlgorithmsKey("INTEGRAL", "0007") -> SearchAllRed,
    AssignAlgorithmsKey("ANALISTA", "7765") -> SearchOnlyTargetedOffice,
    AssignAlgorithmsKey("DIRECTOR", "7765") -> SearchOnlyTargetedOffice,
    AssignAlgorithmsKey("LIDER SOLUCION", "7765") -> SearchOnlyTargetedOffice
  )

  private[RolesInfo] case class AssignAlgorithmsKey(role: String, process: String)

  /**
   * Estado de los roles.
   */
  case class RoleInfoState(roles: Map[String, RoleData] = Map.empty[String, RoleData]) extends ActorWithPersistenceState {

    override def toString: String = {
      val builder = new StringBuilder()
        .append("Roles:\n")
      roles.keys foreach { roleName =>
        builder
          .append(s"\tNombre del rol: $roleName\n")
          .append(s"\tDatos:\n")
          .append(s"\t${roles(roleName)}\n")
      }
      builder.toString()
    }
  }

  /**
   * Información del rol que se guarda en la tabla de roles.
   *
   * @param maxAssignments Máximo número de asignaciones.
   * @param processes Tabla con los procesos asociados al rol y el objeto acompañante de la clase que implementa el
   *                  algoritmo de asignación por rol y proceso.
   */
  private[RolesInfo] case class RoleData(maxAssignments: Int = 0, processes: Map[String, GathererObject] = Map.empty[String, GathererObject]) {

    override def toString: String = {
      val builder = new StringBuilder()
        .append(s"\tMaximo numero de asignaciones: $maxAssignments\n")
        .append(s"\t\tProcesos:\n")
      processes.keys foreach { processCode =>
        builder
          .append(s"\t\t\tCódigo del proceso: $processCode\n")
          .append(s"\t\t\tObjeto para asignación: ${processes(processCode)}\n")
      }
      builder.toString()
    }
  }

  /**
   * Comando que inicia la actualización del máximo número de asignaciones del rol dado.
   *
   * @param roleName Nombre del rol a actualizar.
   * @param roleMaxAssignments Nuevo máximo número de asignaciones.
   */
  case class ModifyMaxAssignments(roleName: String, roleMaxAssignments: Int) extends Command

  /**
   * Commando que inicia la recolección del máximo número de asignaciones para el rol dado.
   */
  case class GiveMaxAssignments(roleName: String) extends Command

  /**
   * Comando que inicia la recolección del objeto [[GathererObject]] acompañante del gatherer que implementa el
   * algoritmo de asignación del rol y proceso dados.
   *
   * @param originalSender Actor que inicio la recolección.
   * @param listener Actor al que se le responderá.
   * @param maxResponses Máximo número de respuestas que se esperan recibir.
   * @param request Solicitud que se atenderá.
   * @param roleName Nombre del rol asociado al algoritmo.
   * @param processCode Código del proceso asociado al algoritmo.
   */
  case class GiveAssignGathererObject(
    originalSender: ActorRef, listener: ActorRef, maxResponses: Int, request: Request, roleName: String, processCode: String
  ) extends Command

  /**
   * Commando que inicia la recolección del máximo número de asignaciones por rol.
   */
  case object GiveMaxAssignmentsByRole extends Command

  /**
   * Evento que indica se modificó el máximo número de asignaciones del rol dado.
   */
  case class RoleMaxAssignmentsModified(roleName: String, roleMaxAssignments: Int, srvActor: ActorRef) extends Event

}

/**
 * Maneja la información asociada a cada rol de los trabajadores.
 */
case class RolesInfo() extends ActorWithPersistence[RoleInfoState] {

  /**
   * Estado.
   */
  var state: RoleInfoState = RoleInfoState()

  /**
   * Override para detener el sistema cuando se falla en recuperar el manejador de información de los roles, que solo
   * puede pasar en el inicio.
   */
  override def postStop(): Unit = sys.exit()

  /**
   * Handler de eventos que se persistieron exitosamente y se han recuperado.
   *
   * @param event Evento persistido recuperado.
   */
  def handleRecoveredEvent(event: Event): Unit = event match {
    case RoleMaxAssignmentsModified(roleName, roleMaxNumAssignments, _) => addUpdateRole(roleName, RoleData(maxAssignments = roleMaxNumAssignments))
    case msg => printUnknownRecovery(msg)
  }

  /**
   * Función llamada una vez se haya completado la recuperación del actor de forma exitosa.
   */
  def recoveryCompleted(): Unit = {
    val recovered = state
    val loaded = load()
    state = RoleInfoState()
    removeUndefinedRoles(recovered, loaded)
    warnRecoveredUndefinedRoles(recovered)
    printState()
  }

  /**
   * Maneja los eventos recibidos por las variables.
   */
  def receive: Receive = {
    case msg: ModifyMaxAssignments => process(sender(), msg)
    case msg: GiveMaxAssignments => process(sender(), msg)
    case msg: GiveAssignGathererObject => process(msg)
    case GiveMaxAssignmentsByRole => sender() ! MaxAssignmentsByRole(mapMaxAssignmentsByRole())
    case SaveActorState => trySaveState(state)
    case msg: DeadLetter => reviewDeadLetter(msg)
    case msg => printUnknownMsg(msg)
  }

  /**
   * Handler de eventos que se acaban de enviar a persistir.
   *
   * @param event Evento enviado a persistir.
   */
  def handlePersistedEvent(event: Event): Unit = event match {
    case RoleMaxAssignmentsModified(name, max, serviceActorOrGatherer) =>
      addUpdateRole(name, state.roles(name).copy(maxAssignments = max))
      printState()
      notifyMaxAssignmentsUpdated(name)
      serviceActorOrGatherer ! MaxAssignmentsModifiedSuccessfully

    case msg => printUnknownPersisted(msg)
  }

  /**
   * Procesa el mensaje [[ModifyMaxAssignments]]
   */
  private def process(serviceActor: ActorRef, msg: ModifyMaxAssignments) = {
    state.roles.get(msg.roleName) match {
      case Some(oldData) => persistEvent(RoleMaxAssignmentsModified(msg.roleName, msg.roleMaxAssignments, serviceActor))
      case None => serviceActor ! RoleUndefined
    }
  }

  /**
   * Procesa el mensaje [[GiveMaxAssignments]]
   */
  private def process(sender: ActorRef, msg: GiveMaxAssignments) = {
    sender ! state.roles.get(msg.roleName).map(x => RoleMaxAssignments(x.maxAssignments)).getOrElse(RoleUndefined)
  }

  /**
   * Procesa el mensaje [[GiveAssignGathererObject]]
   */
  private def process(msg: GiveAssignGathererObject) = state.roles.get(msg.roleName) match {
    case Some(data) => data.processes.get(msg.processCode) match {
      case Some(assignGatherer) =>
        sender() ! AssignGathererFounded(msg.originalSender, msg.listener, msg.maxResponses, msg.request, assignGatherer)
      case None => msg.listener ! ProcessNotAssociatedWithRole
    }

    case None => msg.listener ! RoleUndefined
  }

  /**
   * Carga la información asociada a los roles definida en el archivo de configuración.
   */
  private def load() = {
    val roles = mutable.Map.empty[String, RoleData]
    Constants.config.getConfigList("sura.roles").asScala.foreach { roleItem =>
      val roleConfig = roleItem.getConfig("role")
      val roleName = roleConfig.getString("name").normalized
      validateRoleNotRepeated(roles, roleName)
      val roleMaxNumAssignments = roleConfig.getInt("max-number-assignments")
      val roleProcesses = roleConfig.getConfigList("processes").asScala.toVector
      val roleProcessesData = loadRoleProcessesData(roleName, roleProcesses)
      roles.update(roleName, RoleData(roleMaxNumAssignments, roleProcessesData))
    }
    roles.toMap
  }

  /**
   * Valida que el rol dado no se en encuentre ya en la tabla.
   */
  private def validateRoleNotRepeated(roles: mutable.Map[String, RoleData], roleName: String) = {
    if (roles.get(roleName).isDefined) throw ElementRepeatedException(ErrorsConstants.REPEATED_ROLE_FOUND.concat(roleName))
  }

  /**
   * Carga la lista de procesos asociados al rol definidos en el archivo de configuración.
   */
  private def loadRoleProcessesData(roleName: String, roleProcesses: Vector[Config]) = {
    val roleProcessesData = mutable.Map.empty[String, GathererObject]
    roleProcesses.foreach { processItem =>
      val processConfig = processItem.getConfig("process")
      val processCode = processConfig.getString("code").normalized
      validateProcessNotRepeated(roleName, roleProcessesData, processCode)
      val gathererOpt = getAssignGatherer(roleName, processCode)
      validateAssignGatherer(roleName, processCode, gathererOpt)
      roleProcessesData.update(processCode, gathererOpt.get)
    }
    roleProcessesData.toMap
  }

  /**
   * Valida que el rol dado no se en encuentre ya en la tabla.
   */
  private def validateProcessNotRepeated(roleName: String, roleProcessesData: mutable.Map[String, GathererObject], processCode: String) = {
    if (roleProcessesData.get(processCode).isDefined) {
      throw ElementRepeatedException(ErrorsConstants.REPEATED_ROLE_PROCESS_FOUND.format(processCode, roleName))
    }
  }

  /**
   * Valida que gatherer dado exista.
   */
  private def validateAssignGatherer(roleName: String, processCode: String, gatherer: Option[GathererObject]) = {
    if (gatherer.isEmpty) throw AssignmentAlgorithmNotExist(ErrorsConstants.ASSIGN_ALGORITHM_NOT_FOUND.format(roleName, processCode))
  }

  /**
   * Notifica a los trabajadores de la red que el máximo número de asignaciones para el rol ha cambiado.
   */
  private def notifyMaxAssignmentsUpdated(roleName: String) = sendToWorkersSupervisor(UpdateMaxAssignments(roleName, state.roles(roleName).maxAssignments))

  /**
   * Retorna una tabla con el máximo número de asignaciones por rol.
   */
  private def mapMaxAssignmentsByRole() = state.roles.mapValues(_.maxAssignments)

  /**
   * Retorna el objeto acompañante de la clase gatherer que implementa el algoritmo de asignación por rol y proceso.
   */
  private def getAssignGatherer(roleName: String, processCode: String) = assignAlgorithms.get(AssignAlgorithmsKey(roleName, processCode))

  /**
   * Agrega o actualiza el rol a la tabla de roles.
   */
  private def addUpdateRole(roleName: String, roleData: RoleData) = state = state.copy(state.roles + (roleName -> roleData))

  /**
   * Remueve del estado los roles no definidos en el archivo de configuración.
   */
  private def removeUndefinedRoles(recovered: RoleInfoState, loaded: Map[String, RoleData]) = loaded.keys.foreach { roleName =>
    val loadedData = loaded(roleName)
    val roleData = recovered.roles.getOrElse(roleName, loadedData).copy(processes = loadedData.processes)
    addUpdateRole(roleName, roleData)
  }

  /**
   * Imprime un mensaje WARN por cada rol recuperado que ya no esta definido en el archivo de configuración.
   */
  private def warnRecoveredUndefinedRoles(recovered: RoleInfoState) = recovered.roles.keys.foreach { roleName =>
    if (state.roles.get(roleName).isEmpty) logger.warn(LogConstants.RECOVERED_UNDEFINED_ROLE.format(roleName))
  }
}
