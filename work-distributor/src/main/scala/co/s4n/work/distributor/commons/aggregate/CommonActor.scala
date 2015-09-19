package co.com.sura.distribucion.asignacion.node.commons.aggregate

import akka.actor.Actor
import com.typesafe.scalalogging.LazyLogging

import co.com.sura.distribucion.asignacion.node.commons.constants.{ CoreConstants, LogConstants }
import co.com.sura.distribucion.asignacion.node.core.SystemActors

/**
 * Define funciones comunes entre los actores del sistema.
 *
 * @author Andres Ricardo Peralta Perea.
 */
trait CommonActor extends Actor with LazyLogging {

  /**
   * Re-envía el mensaje dado al supervisor de las oficinas.
   *
   * @param msg Mensaje que será remitido.
   */
  def fwdToOfficesSupervisor(msg: Any): Unit = context.actorSelection(SystemActors.officesSupervisorPath) forward msg

  /**
   * Re-envía el mensaje dado al supervisor de los trabajadores.
   *
   * @param msg Mensaje que será remitido.
   */
  def fwdToWorkersSupervisor(msg: Any): Unit = context.actorSelection(SystemActors.workersSupervisorPath) forward msg

  /**
   * Re-envía el mensaje dado a la oficina identificada con el código dado.
   *
   * @param msg Mensaje que será remitido.
   * @param officeCode Código de la oficina
   */
  def fwdToOffice(msg: Any, officeCode: String): Unit = {
    val actorName = CoreConstants.OFFICE_PREFIX.concat(officeCode)
    context.actorSelection(s"${SystemActors.officesSupervisorPath.toStringWithoutAddress}/$actorName") forward msg
  }

  /**
   * Re-envía el mensaje dado al trabajador identificado con el id dado.
   *
   * @param msg Mensaje que será remitido.
   * @param workerID Identificador del trabajador.
   */
  def fwdToWorker(msg: Any, workerID: String): Unit = {
    val actorName = CoreConstants.WORKER_PREFIX.concat(workerID)
    context.actorSelection(s"${SystemActors.workersSupervisorPath.toStringWithoutAddress}/$actorName") forward msg
  }

  /**
   * Re-envía el mensaje dado al actor que maneja el máximo número de asignaciones para el rol de los trabajadores.
   *
   * @param msg Mensaje que será remitido.
   */
  def fwdToRolesInfo(msg: Any): Unit = context.actorSelection(s"${SystemActors.rolesInfoSupervisorPath.toStringWithoutAddress}/roles-info") forward msg

  /**
   * Envía el mensaje dado al supervisor de los trabajadores.
   *
   * @param msg Mensaje que será enviado.
   */
  def sendToWorkersSupervisor(msg: Any): Unit = context.actorSelection(SystemActors.workersSupervisorPath) ! msg

  /**
   * Envía el mensaje dado al supervisor de las oficinas.
   *
   * @param msg Mensaje que será enviado.
   */
  def sendToOfficesSupervisor(msg: Any): Unit = context.actorSelection(SystemActors.officesSupervisorPath) ! msg

  /**
   * Envía el mensaje dado a la regional identificada con el código dado.
   *
   * @param msg Mensaje que será enviado.
   * @param regionalCode Código de la oficina
   */
  def sendToRegional(msg: Any, regionalCode: String): Unit = {
    val actorName = CoreConstants.REGIONAL_PREFIX.concat(regionalCode)
    context.actorSelection(s"${SystemActors.redPath.toStringWithoutAddress}/$actorName") ! msg
  }

  /**
   * Envía el mensaje dado a la oficina identificada con el código dado.
   *
   * @param msg Mensaje que será enviado.
   * @param officeCode Código de la oficina
   */
  def sendToOffice(msg: Any, officeCode: String): Unit = {
    val actorName = CoreConstants.OFFICE_PREFIX.concat(officeCode)
    context.actorSelection(s"${SystemActors.officesSupervisorPath.toStringWithoutAddress}/$actorName") ! msg
  }

  /**
   * Envía el mensaje dado al trabajador identificado con el id dado.
   *
   * @param msg Mensaje que será enviado.
   * @param workerID Identificador del trabajador.
   */
  def sendToWorker(msg: Any, workerID: String): Unit = {
    val actorName = CoreConstants.WORKER_PREFIX.concat(workerID)
    context.actorSelection(s"${SystemActors.workersSupervisorPath.toStringWithoutAddress}/$actorName") ! msg
  }

  /**
   * Envía el mensaje dado al actor que maneja el máximo número de asignaciones para el rol de los trabajadores.
   *
   * @param msg Mensaje que será enviado.
   */
  def sendToRolesInfo(msg: Any): Unit = context.actorSelection(s"${SystemActors.rolesInfoSupervisorPath.toStringWithoutAddress}/roles-info") ! msg

  /**
   * Envía el mensaje dado al actor que maneja la red de regionales.
   *
   * @param msg Mensaje que será enviado.
   */
  def sendToRed(msg: Any): Unit = context.actorSelection(SystemActors.redPath) ! msg

  protected def printUnknownMsg(msg: Any) = logger.warn(LogConstants.ACTOR_UNKNOWN_MSG.format(self.path.toStringWithoutAddress, msg))

  protected def printState(state: Any) = logger.debug(LogConstants.ACTOR_STATE.format(self.path.toStringWithoutAddress).concat(state.toString))
}
