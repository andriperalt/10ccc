package co.com.sura.distribucion.asignacion.node.commons.ws

// scalastyle:off number.of.types

/**
 * Respuesta a una solicitud que indica no hay red para responder.
 */
case object NotRed extends Response

/**
 * Respuesta a una solicitud indica el rol dado no existe.
 */
case object RoleUndefined extends Response

/**
 * Respuesta a una solicitud de modificación del máximo número de asignaciones de un rol de los trabajadores que
 * indica se modificó exitosamente.
 */
case object MaxAssignmentsModifiedSuccessfully extends Response

/**
 * Respuesta a una solicitud que indica la oficina en cuestión no existe.
 */
case object OfficeNotExists extends Response

/**
 * Respuesta a una solicitud que indica el trabajador en cuestión existe.
 */
case object WorkerExists extends Response

/**
 * Respuesta a una solicitud de creación de un trabajador que indica hubo un problema inesperado con la creación.
 *
 * @param cause Mensaje explicativo del porque no fue exitosa la creación del trabajador.
 */
case class CreateWorkerUnsuccessful(cause: String) extends Response

/**
 * Respuesta a una solicitud de creación de un trabajador que indica se creó exitosamente.
 */
case object WorkerCreatedSuccessfully extends Response

/**
 * Respuesta a una solicitud indica el proceso dado no esta asociado al rol.
 */
case object ProcessNotAssociatedWithRole extends Response

/**
 * Respuesta a una solicitud indica el proceso dado no esta asociado al rol.
 */
case object NotOneWithCapacity extends Response

/**
 * Respuesta a una solicitud de asignación de un trabajador que indica se realizo correctamente.
 */
case class WorkerSuccessfullyAssigned(
  login: String, capacidad: Int, codigoOficina: String, nombreOficina: String, capacidadOficina: Int, codigoRegional: String, nombreRegional: String,
  capacidadRegional: Int
) extends Response

/**
 * Respuesta a una solicitud que indica el trabajador en cuestión no existe.
 */
case object WorkerNotExists extends Response

/**
 * Respuesta a una solicitud que indica el trabajador en cuestión tiene tareas asignadas.
 *
 * @param assignments Número de tareas asignadas que tiene el trabajador.
 */
case class WorkerHasAssignedTasks(assignments: Int) extends Response

/**
 * Respuesta a una solicitud de inhabilitación de un trabajador que indica ya se inhabilito con anterioridad.
 */
case object WorkerAlreadyDisabled extends Response

/**
 * Respuesta a una solicitud de inhabilitación de un trabajador que indica se realizo correctamente.
 */
case object WorkerSuccessfullyDisabled extends Response

/**
 * Respuesta a una solicitud de habilitación de un trabajador que indica ya se habilito con anterioridad.
 */
case object WorkerAlreadyEnabled extends Response

/**
 * Respuesta a una solicitud de habilitación de un trabajador que indica se realizo correctamente.
 */
case object WorkerSuccessfullyEnabled extends Response

/**
 * Respuesta a una solicitud de eliminación de un trabajador que indica se eliminó correctamente el trabajador.
 */
case object WorkerDeletedSuccessfully extends Response

/**
 * Respuesta a una solicitud de transferencia de oficina de un trabajador que indica el trabajador ya se encuentra en la
 * oficina a la que se le intenta transferir.
 */
case object WorkerAlreadyThere extends Response

/**
 * Respuesta a una solicitud de transferencia de oficina de un trabajador que indica se transfirió correctamente el
 * trabajador.
 */
case object WorkerSuccessfullyTransferred extends Response

/**
 * Respuesta a una solicitud de transferencia de asignaciones entre trabajadores que indica el trabajador dado no
 * existe.
 */
case class WorkerTransferAssignmentsNotExists(workerID: String) extends Response

/**
 * Respuesta a una solicitud  que indica el trabajador no tiene suficientes solicitudes asignadas para la operación.
 *
 * @param workerAssignments Numero de solicitudes asignadas actualmente al trabajador.
 */
case class WorkerNotEnoughAssignments(workerAssignments: Int) extends Response

/**
 * Respuesta a una solicitud de transferencia de asignaciones entre trabajadores que indica los trabajadores tienen
 * roles diferentes.
 *
 * @param workerRole Rol del trabajador origen.
 */
case class WorkersHaveDifferentRoles(workerRole: String) extends Response

/**
 * Respuesta a una solicitud de transferencia de asignaciones entre trabajadores que indica no se pueden transferir
 * las tareas ya que superan el límite de asignaciones para el rol dado
 *
 * @param workerAssignments Maximo número de asignaciones que puede recibir el trabajador destino.
 */
case class AssignmentsWorkerDestinationOverLimit(workerAssignments: Int) extends Response

/**
 * Respuesta a una solicitud de transferencia de asignaciones entre trabajadores que indica se transfirieron
 * correctamente las asignaciones.
 */
case object AssignmentsSuccessfullyTransferred extends Response

/**
 * Respuesta a una solicitud de remoción de un trabajador que indica se realizo correctamente.
 */
case object AssignmentsSuccessfullyUnassigned extends Response

case object ChangeRoleSuccessfully extends Response

/**
 * Respuesta a una solicitud de información de la red.
 */
case class InfoRed(roles: Seq[InfoRol], regionales: Seq[InfoRegional]) extends Response

/**
 * Información de cada rol de la red.
 */
case class InfoRol(nombre: String, maximoNumeroSolicitudes: Int) extends Serializable

/**
 * Información de cada regional de la red.
 */
case class InfoRegional(codigo: String, nombre: String, porcentajeOcupacion: String, oficinas: Seq[InfoOficina]) extends Serializable

/**
 * Información de cada oficina de una regional.
 */
case class InfoOficina(
  codigo: String, nombre: String, capacidadTotal: Int, capacidadOcupada: Int, porcentajeOcupacion: String, numeroTotalPersonas: Int,
  roles: Seq[InfoRoleOficina]
) extends Serializable

/**
 * Información de cada rol de una oficina.
 */
case class InfoRoleOficina(nombre: String, numeroPersonas: Int, personas: Seq[InfoPersona]) extends Serializable

/**
 * Información de cada persona de un rol.
 */
case class InfoPersona(login: String, cargo: String, numeroSolicitudes: Int) extends Serializable

/**
 * Respuesta a una solicitud de un servicio
 */
sealed trait Response extends Serializable

// scalastyle:on number.of.types
