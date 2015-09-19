package co.com.sura.distribucion.asignacion.node.regionals.aggregate

import akka.actor._
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection.mutable

import co.com.sura.distribucion.asignacion.node.commons.util.StringUtils._
import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ Command, CommonActor, NotOfficesWithCapacity, OfficesWithCapacity }
import co.com.sura.distribucion.asignacion.node.commons.ws.{ InfoOficina, InfoRegional, WorkerSuccessfullyAssigned }
import co.com.sura.distribucion.asignacion.node.offices.aggregate.OfficesSupervisor.CreateOffice
import co.com.sura.distribucion.asignacion.node.red.aggregate.Red.{ RedRoleData, UpdateRegionalInfo }
import co.com.sura.distribucion.asignacion.node.regionals.aggregate.Regional._

/**
 * Fábrica de instancias de [[Regional]]
 * Define los mensajes específicos procesados en última instancia por una regional.
 */
object Regional {

  /**
   * Retorna Props del actor [[Regional]].
   */
  def props(name: String, code: String, config: Config): Props = Props(Regional(name, code, config))

  /**
   * Estado de la regional.
   *
   * @param maxAssignments Número de asignaciones que soporta la regional.
   * @param assignments Número de asignaciones que tiene la regional.
   * @param occupancyPercentage Porcentaje de ocupación de la regional.
   * @param offices Tabla con la información de las oficinas de la regional.
   * @param roles Tabla con la información de las oficinas de la regional por rol.
   */
  private[Regional] case class RegionalState(
      maxAssignments: Int = 0, assignments: Int = 0, occupancyPercentage: Double = 0.0, offices: Map[String, OfficeData] = Map.empty[String, OfficeData],
      roles: Map[String, RegionalRoleData] = Map.empty[String, RegionalRoleData]
  ) {

    override def toString: String = {
      val builder = new StringBuilder()
        .append(s"Maximo numero de asignaciones: $maxAssignments\n")
        .append(s"Numero de asignaciones actuales: $assignments\n")
        .append(s"Porcentaje de ocupación actual: $occupancyPercentage%\n")
        .append("Oficinas:\n")
      offices foreach { e =>
        val (officeCode, officeData) = e
        builder.append(s"Código: $officeCode\n")
          .append(s"\tDatos:\n")
          .append(s"\t$officeData\n")
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
   * Información de los roles de las oficinas de la regional.
   *
   * @param maxAssignments Máximo número de asignaciones para el rol.
   * @param assignments Número de asignaciones actuales del rol.
   * @param offices Tabla con la información de las oficinas filtradas por rol.
   */
  case class RegionalRoleData(
      maxAssignments: Int = 0, assignments: Int = 0, offices: Map[String, OfficeData] = Map.empty[String, OfficeData]
  ) {

    override def toString: String = {
      val builder = new StringBuilder()
        .append(s"\tMaximo numero de asignaciones: $maxAssignments\n")
        .append(s"\t\tNumero de asignaciones actuales: $assignments\n")
        .append(s"\t\tOficinas:\n")
      offices foreach { e =>
        val (officeCode, officeData) = e
        builder.append(s"\t\t\tCódigo: $officeCode\n")
          .append(s"\t\t\tDatos:\n")
          .append(s"\t\t\t$officeData\n")
      }
      builder.toString()
    }
  }

  /**
   * Información de la oficina que se guarda en la tabla de oficinas de la regional.
   *
   * @param officeName Nombre de la oficina.
   * @param maxAssignments Número de asignaciones que soporta la oficina.
   * @param assignments Número de asignaciones de la oficina.
   */
  private[Regional] case class OfficeData(officeName: String, maxAssignments: Int = 0, assignments: Int = 0) {

    override def toString: String = {
      val builder = new StringBuilder()
        .append(s"\tNombre: $officeName\n")
        .append(s"\t\t\t\tMaximo numero de asignaciones: $maxAssignments\n")
        .append(s"\t\t\t\tNumero de asignaciones actuales: $assignments\n")
      builder.toString()
    }
  }

  /**
   * Comando que inicia la actualización de la información de la oficina dada.
   *
   * @param officeCode Código de la oficina.
   * @param maxAssignments Máximo número de asignaciones de la oficina.
   * @param assignments Número de asignaciones de la oficina.
   * @param roles Tabla con la información de los roles de la oficina.
   */
  case class UpdateOfficeInfo(officeCode: String, maxAssignments: Int, assignments: Int, roles: Map[String, RegionalRoleData]) extends Command

  /**
   * Comando que inicia la recolección de las oficinas de la regional que tienen capacidad para recibir las asignaciones
   * dadas para el rol dado y no son la oficina dada ya buscada.
   *
   * @param officeCode Código de la oficina que ya se busco.
   * @param role Rol del trabajador que se busca asignar.
   * @param assignments Número de solicitudes que se buscan asignar.
   */
  case class GiveOfficesWithCapacity(officeCode: String, role: String, assignments: Int) extends Command

  /**
   * Comando que inicia la recolección de la información para el servicio de asignación de la oficina asignada.
   */
  case class BuildOfficeAssigned(
    workerID: String, workerMaxAssignments: Int, officeCode: String, officeName: String, officeMaxAssignments: Int, gatherer: ActorRef
  ) extends Command

  /**
   * Comando que inicia la recolección de la información de la regional para la información de la red.
   */
  case class GiveRegionalInfo(officeInfo: InfoOficina, gatherer: ActorRef) extends Command

}

/**
 * Una regional de la red.
 *
 * @param name Nombre de la regional.
 * @param code Código de la regional.
 * @param config Configuración de la regional.
 */
case class Regional(name: String, code: String, config: Config) extends CommonActor {

  /**
   * Estado actual de la regional.
   */
  private[Regional] var state: RegionalState = RegionalState()

  /**
   * Override para crear e iniciar todas las oficinas de la regional una sola vez.
   */
  override def preStart() {
    config.getConfigList("offices").asScala.foreach { officesItem =>
      val officeConfig = officesItem.getConfig("office")
      val officeName = officeConfig.getString("name").normalized
      val officeCode = officeConfig.getString("code").normalized
      sendToOfficesSupervisor(CreateOffice(code, officeName, officeCode))
      addUpdateOffice(officeCode, OfficeData(officeName = officeName))
    }
    logState()
  }

  /**
   * Maneja los mensajes recibidos por la regional.
   */
  def receive: Receive = {
    case msg: UpdateOfficeInfo => process(msg)
    case msg: GiveOfficesWithCapacity => process(sender(), msg)
    case msg: BuildOfficeAssigned => process(msg)
    case GiveRegionalInfo(officeInfo, gatherer) => gatherer ! InfoRegional(code, name, s"${state.occupancyPercentage}%", Seq(officeInfo))
    case msg => printUnknownMsg(msg)
  }

  /**
   * Procesa el mensaje [[UpdateOfficeInfo]]
   */
  private def process(msg: UpdateOfficeInfo) = {
    val oldOfficeData = state.offices(msg.officeCode)
    msg.roles.keys.foreach { roleName =>
      val newRoleData = msg.roles(roleName)
      val oldRoleData = state.roles.getOrElse(roleName, RegionalRoleData())
      updateRoleData(roleName, msg.officeCode, oldOfficeData, oldRoleData, newRoleData)
    }
    updateAssignmentsOffice(msg.officeCode, oldOfficeData, msg.maxAssignments, msg.assignments)
    logState()
    updateInfoRed()
  }

  /**
   * Procesa el mensaje [[GiveOfficesWithCapacity]]
   */
  private def process(sender: ActorRef, msg: GiveOfficesWithCapacity) = {
    val assignments = state.assignments + msg.assignments
    val offices = if (assignments <= state.maxAssignments && state.roles.get(msg.role).nonEmpty) {
      giveOfficesWithCapacity(msg.assignments, msg.officeCode, state.roles(msg.role))
    } else {
      Map.empty[String, Int]
    }
    if (offices.isEmpty) sender ! NotOfficesWithCapacity else sender ! OfficesWithCapacity(offices)
  }

  /**
   * Procesa el mensaje [[BuildOfficeAssigned]]
   */
  private def process(msg: BuildOfficeAssigned) = {
    val response = WorkerSuccessfullyAssigned(
      msg.workerID, msg.workerMaxAssignments, msg.officeCode, msg.officeName, msg.officeMaxAssignments, code, name, state.maxAssignments
    )
    msg.gatherer ! response
  }

  /**
   * Actualiza la tabla de roles de la regional con la información del rol entregada por la oficina.
   */
  private def updateRoleData(roleName: String, officeCode: String, officeData: OfficeData, oldRoleData: RegionalRoleData, newRoleData: RegionalRoleData) = {
    val oldRoleOfficeData = oldRoleData.offices.getOrElse(officeCode, OfficeData(officeName = officeData.officeName))
    val newRoleOfficeData = oldRoleOfficeData.copy(maxAssignments = newRoleData.maxAssignments, assignments = newRoleData.assignments)
    val actualRoleData = oldRoleData.copy(
      maxAssignments = oldRoleData.maxAssignments - oldRoleOfficeData.maxAssignments + newRoleOfficeData.maxAssignments,
      assignments = oldRoleData.assignments - oldRoleOfficeData.assignments + newRoleOfficeData.assignments
    )
    addUpdateRoleOffice(roleName, actualRoleData, officeCode, newRoleOfficeData)
  }

  /**
   * Actualiza la información de las asignaciones de la oficina dada.
   */
  private def updateAssignmentsOffice(officeCode: String, officeData: OfficeData, maxAssignments: Int, assignments: Int) = {
    addUpdateOffice(officeCode, officeData.copy(maxAssignments = maxAssignments, assignments = assignments))
    updateMaxAssignments(maxAssignments - officeData.maxAssignments)
    updateAssignments(assignments - officeData.assignments)
  }

  /**
   * Busca las oficinas que tienen capacidad para recibir las asignaciones dadas en el rol dado.
   */
  private def giveOfficesWithCapacity(assignments: Int, officeCode: String, roleData: RegionalRoleData) = {
    val offices = mutable.Map.empty[String, Int]
    val roleAssignments = roleData.assignments + assignments
    if (roleAssignments <= roleData.maxAssignments) {
      roleData.offices.keys foreach { regionalOfficeCode =>
        val officeData = roleData.offices(regionalOfficeCode)
        val officeAssignments = officeData.assignments + assignments
        if (officeAssignments <= officeData.maxAssignments && !regionalOfficeCode.equals(officeCode)) {
          offices.update(regionalOfficeCode, officeData.assignments)
        }
      }
    }
    offices.toMap
  }

  /**
   * Agrega o actualiza una oficina a la tabla de oficinas por rol de la regional.
   */
  private def addUpdateRoleOffice(roleName: String, roleData: RegionalRoleData, officeCode: String, officeData: OfficeData) = {
    addUpdateRole(roleName, roleData.copy(offices = roleData.offices + (officeCode -> officeData)))
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
   * Agrega una oficina a la tabla de oficinas de la regional.
   *
   * @param officeCode Identificador de la oficina.
   * @param data Información de la oficina.
   */
  private def addUpdateOffice(officeCode: String, data: OfficeData) = state = state.copy(offices = state.offices + (officeCode -> data))

  /**
   * Actualiza el porcentaje de ocupación de la oficina.
   */
  private def updateOccupancyPercentage() = {
    if (state.maxAssignments > 0) {
      val percentage = (state.assignments.toDouble / state.maxAssignments.toDouble) * 100.0
      val occupancyPercentage = BigDecimal(percentage).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      state = state.copy(occupancyPercentage = occupancyPercentage)
    }
  }

  /**
   * Agrega el rol a la tabla de roles de la oficina.
   */
  private def addUpdateRole(roleName: String, roleData: RegionalRoleData) = state = state.copy(roles = state.roles + (roleName -> roleData))

  /**
   * Envía la información de la regional a la red para que se actualice.
   */
  private def updateInfoRed() = {
    val roles = mutable.Map.empty[String, RedRoleData]
    state.roles.keys.foreach { roleName =>
      val roleData = state.roles(roleName)
      val redRoleData = RedRoleData(maxAssignments = roleData.maxAssignments, assignments = roleData.assignments)
      roles.update(roleName, redRoleData)
    }
    sendToRed(UpdateRegionalInfo(code, state.maxAssignments, state.assignments, roles.toMap))
  }

  /**
   * Imprime en modo DEBUG el estado del actor actualizado.
   */
  private def logState() = printState(state)
}
