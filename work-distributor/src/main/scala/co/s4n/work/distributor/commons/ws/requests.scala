package co.com.sura.distribucion.asignacion.node.commons.ws

import org.joda.time.DateTime

import scala.util.Try

import co.com.sura.distribucion.asignacion.node.commons.constants.ErrorsConstants

/**
 * Solicitud de modificación del máximo número de asignaciones de un rol de los trabajadores.
 *
 * @param rol Rol a modificar.
 * @param maximoNumeroAsignaciones máximo número de asignaciones del rol.
 */
case class ModifyMaxNumAssignmentsRQ(rol: String, maximoNumeroAsignaciones: Int) extends Request {

  require(maximoNumeroAsignaciones > 0, ErrorsConstants.INVALID_MAX_ASSIGNMENTS_FOR_ROLE)
}

/**
 * Solicitud de creación de un trabajador.
 *
 * @param codigoOficina Identificador de la oficina a la que pertenece el trabajador.
 * @param login Identificación del trabajador a crear.
 * @param rol Rol del trabajador a crear.
 */
case class CreateWorkerRQ(codigoOficina: String, login: String, rol: String) extends Request

/**
 * Solicitud de asignación de un trabajador.
 *
 * @param codigoOficina Código de la oficina donde de intentara asignar.
 * @param numeroSolicitudes Número de solicitudes que se asignaran.
 * @param rol Rol del trabajador que se asignara.
 * @param codigoProceso Código del proceso que se busca asignar.
 */
case class AssignWorkerRQ(codigoOficina: String, numeroSolicitudes: Int, rol: String, codigoProceso: String) extends Request {

  require(numeroSolicitudes > 0, ErrorsConstants.INVALID_ASSIGNMENTS)
  require(Try(codigoProceso.toInt).isSuccess, ErrorsConstants.INVALID_PROCESS_CODE)
}

/**
 * Solicitud de inhabilitación de un trabajador.
 *
 * @param login Identificación del trabajador.
 * @param fechaHabilitacion Fecha en la que el trabajador se debe volver a habilitar.
 */
case class DisableWorkerRQ(login: String, fechaHabilitacion: DateTime) extends Request {

  require(fechaHabilitacion.isAfterNow, ErrorsConstants.INVALID_SELF_ENABLING_DATE)
}

/**
 * Solicitud de habilitación de un trabajador.
 *
 * @param login Identificación del trabajador.
 */
case class EnableWorkerRQ(login: String) extends Request

/**
 * Solicitud de eliminación de un trabajador.
 *
 * @param login Identificación del trabajador a eliminar.
 */
case class DeleteWorkerRQ(login: String) extends Request

/**
 * Solicitud de transferencia de oficina de un trabajador.
 *
 * @param login Identificación del trabajador a eliminar.
 * @param codigoOficina Código de la oficina a la que se transferirá el trabajador.
 */
case class TransferWorkerRQ(login: String, codigoOficina: String) extends Request

/**
 * Solicitud de transferencia de asignaciones entre trabajadores.
 */
case class TransferAssignmentsRQ(loginOrigen: String, loginDestino: String, numeroSolicitudes: Int) extends Request {

  require(numeroSolicitudes > 0, ErrorsConstants.INVALID_ASSIGNMENTS)
  require(!loginOrigen.equalsIgnoreCase(loginDestino), ErrorsConstants.SAME_SOURCE_DESTINATION_WORKER)
}

/**
 * Solicitud de remoción de solicitudes a un trabajador.
 *
 * @param login Login del trabajador al que se le des-asignaran las solicitudes.
 * @param numeroSolicitudes Número de solicitudes que se des-asignaran.
 */
case class UnassignWorkerRQ(login: String, numeroSolicitudes: Int) extends Request {

  require(numeroSolicitudes > 0, ErrorsConstants.INVALID_ASSIGNMENTS)
}

/**
 * Solicitud de información de la red.
 */
case class GiveInfoRedRQ() extends Request

/**
 * Solicitud de cambio de Rol de un trabajador.
 *
 * @param login Identificación del trabajador a al que se le cambiara el rol.
 * @param rol Nuevo ol del trabajador.
 */
case class ChangeWorkerRoleRQ(login: String, rol: String) extends Request

/**
 * Solicitud a un servicio
 */
sealed trait Request extends Serializable
