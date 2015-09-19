package co.com.sura.distribucion.asignacion.node.red.aggregate

import akka.actor.{ ActorRef, ActorSelection, Address, RootActorPath }
import akka.cluster.ClusterEvent._
import akka.cluster.{ Cluster, Member }

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

import co.com.sura.distribucion.asignacion.node.commons.constants.{ ClusterConstants, Constants, CoreConstants }
import co.com.sura.distribucion.asignacion.node.commons.util.StringUtils._
import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate._
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.red.aggregate.Red._
import co.com.sura.distribucion.asignacion.node.regionals.aggregate.Regional
import co.com.sura.distribucion.asignacion.node.roles.aggregate.RolesInfo.ModifyMaxAssignments

/**
 * Define los mensajes específicos procesados en última instancia por la red.
 */
object Red {

  /**
   * Estado de la red.
   *
   * @param regionals Tabla con la información de las regionales de la red.
   * @param roles Tabla con la información de las oficinas de la red por rol.
   */
  private[Red] case class RedState(
      regionals: Map[String, RegionalData] = Map.empty[String, RegionalData], roles: Map[String, RedRoleData] = Map.empty[String, RedRoleData]
  ) {

    override def toString: String = {
      val builder = new StringBuilder()
        .append("Regionales:\n")
      regionals.foreach {
        case (regionalCode, regionalData) =>
          builder
            .append(s"Código: $regionalCode\n")
            .append(s"\tDatos:\n")
            .append(s"\t$regionalData\n")
      }
      builder.append("Roles:\n")
      roles foreach { e =>
        val (roleName, roleData) = e
        builder.append(s"\tNombre: $roleName\n")
          .append(s"\tDatos:\n")
          .append(s"\t$roleData\n")
      }
      builder.toString()
    }
  }

  /**
   * Información de los roles de las regionales de la red.
   *
   * @param maxAssignments Máximo número de asignaciones para el rol.
   * @param assignments Número de asignaciones actuales del rol.
   * @param regionals Tabla con la información de las regionales filtradas por rol.
   */
  case class RedRoleData(maxAssignments: Int = 0, assignments: Int = 0, regionals: Map[String, RegionalData] = Map.empty[String, RegionalData]) {

    override def toString: String = {
      val builder = new StringBuilder()
        .append(s"\tMaximo numero de asignaciones: $maxAssignments\n")
        .append(s"\t\tNumero de asignaciones actuales: $assignments\n")
        .append(s"\t\tRegionales:\n")
      regionals.foreach { e =>
        val (regionalCode, regionalData) = e
        builder.append(s"\t\t\tCódigo: $regionalCode\n")
          .append(s"\t\t\tDatos:\n")
          .append(s"\t\t\t$regionalData\n")
      }
      builder.toString()
    }
  }

  /**
   * Información de la regional que se guarda en la tabla de regionales de la red.
   *
   * @param regionalName Nombre de la regional.
   * @param maxAssignments Número de asignaciones que soporta la regional.
   * @param assignments Número de asignaciones de la regional.
   */
  case class RegionalData(regionalName: String, maxAssignments: Int = 0, assignments: Int = 0) {

    override def toString: String = {
      val builder = new StringBuilder()
        .append(s"\tNombre: $regionalName\n")
        .append(s"\t\t\t\tMaximo numero de asignaciones: $maxAssignments\n")
        .append(s"\t\t\t\tNumero de asignaciones actuales: $assignments\n")
      builder.toString()
    }
  }

  /**
   * Comando que inicia la actualización de la información de la regional dada.
   *
   * @param regionalCode Código de la regional.
   * @param maxAssignments Máximo número de asignaciones de la regional.
   * @param assignments Número de asignaciones de la regional.
   * @param roles Tabla con la información de los roles de la regional.
   */
  case class UpdateRegionalInfo(regionalCode: String, maxAssignments: Int, assignments: Int, roles: Map[String, RedRoleData]) extends Command

  /**
   * Comando que inicia la recolección de las regionales de la red que tienen capacidad para recibir las asignaciones
   * dadas para el rol dado y no son la regional dada ya buscada.
   *
   * @param regionalCode Código de la regional que ya se busco.
   * @param role Rol del trabajador que se busca asignar.
   * @param assignments Número de solicitudes que se buscan asignar.
   */
  case class GiveRegionalsWithCapacity(regionalCode: String, role: String, assignments: Int) extends Command

}

/**
 * Red principal del sistema.
 */
case class Red() extends CommonActor {

  private[Red] val cluster = Cluster(context.system)
  private[Red] var iAmLeader = false
  private[Red] val brothers = ListBuffer.empty[ActorSelection]

  /**
   * Estado actual de la red.
   */
  private[Red] var state: RedState = RedState()

  /**
   * Override para crear e iniciar todas las regionales de la red una sola vez.
   */
  override def preStart(): Unit = {
    cluster.subscribe(
      self, classOf[MemberUp], classOf[MemberExited], classOf[MemberRemoved], classOf[RoleLeaderChanged], classOf[UnreachableMember], classOf[ReachableMember]
    )
    loadClusterState()
    init()
    logState()
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    sys.exit()
  }

  // scalastyle:off cyclomatic.complexity
  /**
   * Maneja los mensajes recibidos por la red.
   */
  def receive: Receive = {
    case msg: CurrentClusterState => process(msg)
    case MemberUp(m) => registerNode(m)
    case MemberExited(m) => unregisterNode(m)
    case MemberRemoved(m, _) => unregisterNode(m)
    case msg: RoleLeaderChanged => process(msg)
    case UnreachableMember(m) => unregisterNode(m)
    case ReachableMember(m) => registerNode(m)
    case msg: UpdateRegionalInfo => process(msg)
    case msg: GiveRegionalsWithCapacity => process(sender(), msg)
    case msg: Request => process(msg)
    case msg => printUnknownMsg(msg)
  }
  // scalastyle:on cyclomatic.complexity

  private def process(msg: CurrentClusterState) = {
    reviewLeader(msg.roleLeader(ClusterConstants.ROLE_NODE))
  }

  private def process(msg: RoleLeaderChanged) = if (msg.role.equals(ClusterConstants.ROLE_NODE)) reviewLeader(msg.leader)

  // scalastyle:off cyclomatic.complexity
  private def process(msg: Request): Unit = {
    if (iAmLeader) brothers.foreach(brother => brother.tell(msg, ActorRef.noSender))
    msg match {
      case ModifyMaxNumAssignmentsRQ(rol, maximoNumeroAsignaciones) => fwdToRolesInfo(ModifyMaxAssignments(rol, maximoNumeroAsignaciones))
      case msg: CreateWorkerRQ => fwdToOfficesSupervisor(msg)
      case msg: AssignWorkerRQ => fwdToOfficesSupervisor(msg)
      case msg: DisableWorkerRQ => fwdToWorkersSupervisor(msg)
      case msg: EnableWorkerRQ => fwdToWorkersSupervisor(msg)
      case msg: DeleteWorkerRQ => fwdToWorkersSupervisor(msg)
      case msg: TransferWorkerRQ => fwdToOfficesSupervisor(msg)
      case msg: TransferAssignmentsRQ => fwdToWorkersSupervisor(msg)
      case msg: UnassignWorkerRQ => fwdToWorkersSupervisor(msg)
      case msg: GiveInfoRedRQ => fwdToOfficesSupervisor(msg)
      case msg: ChangeWorkerRoleRQ => fwdToWorkersSupervisor(msg)
      case _ => printUnknownMsg(msg)
    }
  }
  // scalastyle:on cyclomatic.complexity

  /**
   * Procesa el mensaje [[UpdateRegionalInfo]]
   */
  private def process(msg: UpdateRegionalInfo) = {
    val oldRegionalData = state.regionals(msg.regionalCode)
    msg.roles.foreach {
      case (roleName, newRoleData) =>
        val oldRoleData = state.roles.getOrElse(roleName, RedRoleData())
        updateRoleData(roleName, msg.regionalCode, oldRegionalData, oldRoleData, newRoleData)
    }
    updateAssignmentsRegional(msg.regionalCode, oldRegionalData, msg.maxAssignments, msg.assignments)
    logState()
  }

  /**
   * Procesa el mensaje [[GiveRegionalsWithCapacity]]
   */
  private def process(sender: ActorRef, msg: GiveRegionalsWithCapacity) = {
    val regionals = state.roles.get(msg.role)
      .map(roleData => giveRegionalsWithCapacity(msg.assignments, msg.regionalCode, roleData))
      .getOrElse(Map.empty[String, Int])
    if (regionals.isEmpty) sender ! NotRegionalsWithCapacity else sender ! RegionalsWithCapacity(regionals)
  }

  private def loadClusterState(): Unit = {
    logger.debug(s"== Node startup ==")
    brothers.clear()
    cluster.state.members.foreach { member =>
      logger.debug(s"${member.address}")
      registerNode(member)
    }
    process(cluster.state)
  }

  private def registerNode(member: Member) = {
    if (member.hasRole(ClusterConstants.ROLE_NODE) && member.address != cluster.selfAddress) {
      logger.debug("I'm a node and a new node brother was reported")
      val brother = context.actorSelection(RootActorPath(member.address) / "user" / "red")
      brothers += brother
      logger.debug(s"brothers = $brothers")
    }
  }

  private def unregisterNode(member: Member): Unit = {
    logger.debug(s"== Node down ==")
    logger.debug(s"member.roles = ${member.roles}")
    val brother = context.actorSelection(RootActorPath(member.address) / "user" / "red")
    brothers -= brother
    logger.debug(s"brothers = $brothers")
  }

  private def reviewLeader(leaderAddress: Option[Address]) = {
    iAmLeader = false
    leaderAddress.foreach { address =>
      if (address == cluster.selfAddress) {
        iAmLeader = true
        ActorWithPersistence.hasPersistenceToken = true
        logger.debug("I'm a node and I'm the leader")
      }
    }
  }

  private def init() = {
    Constants.config.getConfigList("sura.regionals").asScala.foreach { regionalItem =>
      val regionalConfig = regionalItem.getConfig("regional")
      val regionalName = regionalConfig.getString("name").normalized
      val regionalCode = regionalConfig.getString("code").normalized
      val actorName = CoreConstants.REGIONAL_PREFIX.concat(regionalCode)
      context.actorOf(Regional.props(regionalName, regionalCode, regionalConfig), actorName)
      addUpdateRegional(regionalCode, RegionalData(regionalName))
    }
  }

  /**
   * Actualiza la tabla de roles de la red con la información del rol entregada por la regional.
   */
  private def updateRoleData(roleName: String, regionalCode: String, oldRegionalData: RegionalData, oldRoleData: RedRoleData, newRoleData: RedRoleData) = {
    val oldRoleRegionalData = oldRoleData.regionals.getOrElse(regionalCode, RegionalData(regionalName = oldRegionalData.regionalName))
    val newRoleRegionalData = oldRoleRegionalData.copy(maxAssignments = newRoleData.maxAssignments, assignments = newRoleData.assignments)
    val actualRoleData = oldRoleData.copy(
      maxAssignments = oldRoleData.maxAssignments - oldRoleRegionalData.maxAssignments + newRoleRegionalData.maxAssignments,
      assignments = oldRoleData.assignments - oldRoleRegionalData.assignments + newRoleRegionalData.assignments
    )
    addUpdateRoleRegional(roleName, actualRoleData, regionalCode, newRoleRegionalData)
  }

  /**
   * Actualiza la información de las asignaciones de la oficina dada.
   */
  private def updateAssignmentsRegional(regionalCode: String, regionalData: RegionalData, maxAssignments: Int, assignments: Int) = {
    addUpdateRegional(regionalCode, regionalData.copy(maxAssignments = maxAssignments, assignments = assignments))
  }

  /**
   * Busca las regionales que tienen capacidad para recibir las asignaciones dadas.
   */
  private def giveRegionalsWithCapacity(assignments: Int, regionalCode: String, roleData: RedRoleData) = {
    val roleAssignments = roleData.assignments + assignments
    val regionals = if (roleAssignments <= roleData.maxAssignments) {
      roleData.regionals.filter {
        case (keyRegionalCode, regionalData) =>
          val regionalAssignments = regionalData.assignments + assignments
          regionalAssignments <= regionalData.maxAssignments && !keyRegionalCode.equals(regionalCode)
      }.mapValues(_.assignments)
    } else {
      Map.empty[String, Int]
    }
    regionals
  }

  /**
   * Agrega o actualiza una regional a la tabla de regionales por rol de la red.
   */
  private def addUpdateRoleRegional(roleName: String, roleData: RedRoleData, regionalCode: String, regionalData: RegionalData) = {
    addUpdateRole(roleName, roleData.copy(regionals = roleData.regionals + (regionalCode -> regionalData)))
  }

  /**
   * Agrega el rol a la tabla de roles de la red.
   */
  private def addUpdateRole(roleName: String, roleData: RedRoleData) = state = state.copy(roles = state.roles + (roleName -> roleData))

  /**
   * Agrega una regional a la tabla de regionales de la red.
   *
   * @param regionalCode Llave de la regional.
   * @param data Información de la regional.
   * @return El nuevo estado de la red.
   */
  private def addUpdateRegional(regionalCode: String, data: RegionalData) = state = state.copy(regionals = state.regionals + (regionalCode -> data))

  /**
   * Imprime en modo DEBUG el estado del actor actualizado.
   */
  private def logState() = printState(state)
}
