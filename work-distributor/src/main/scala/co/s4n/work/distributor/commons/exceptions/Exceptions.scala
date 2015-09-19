package co.com.sura.distribucion.asignacion.node.commons.exceptions

/**
 * Excepción de un servicio.
 *
 * @param level Nivel de la excepción [[BusinessLevel]] o [[TechnicalLevel]].
 * @param message Mensaje del error.
 * @param cause Causa del error.
 */
case class ServiceException(level: LevelException, message: String, cause: Option[Throwable])

/**
 * Excepción al intentar recuperar el estado de un actor persistente.
 *
 * @param message Mensaje explicativo del error.
 * @param cause Causa interna del error.
 */
case class RecoveryStateException(message: String, cause: Throwable) extends RuntimeException(message, cause)

/**
 * Excepción al intentar persistir el estado de un actor persistente.
 *
 * @param message Mensaje explicativo del error.
 * @param cause Causa interna del error.
 */
case class PersistStateException(message: String, cause: Throwable) extends RuntimeException(message, cause)

/**
 * Excepción al intentar crear un actor en el sistema.
 *
 * @param message Mensaje explicativo del error.
 * @param cause Causa interna del error.
 */
case class CreateActorException(message: String, cause: Throwable) extends RuntimeException(message, cause)

/**
 * Excepción al intentar cargar un elemento ya cargado.
 *
 * @param message Mensaje explicativo del error.
 */
case class ElementRepeatedException(message: String) extends RuntimeException(message)

/**
 * Excepción al intentar cargar un algoritmo de asignación en base a un rol y proceso.
 *
 * @param message Mensaje explicativo del error.
 */
case class AssignmentAlgorithmNotExist(message: String) extends RuntimeException(message)

/**
 * Excepción de negocio.
 */
case object BusinessLevel extends LevelException

/**
 * Excepción técnica.
 */
case object TechnicalLevel extends LevelException

/**
 * Nivel de una excepción.
 */
trait LevelException
