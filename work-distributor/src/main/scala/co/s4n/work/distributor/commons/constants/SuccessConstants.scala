package co.com.sura.distribucion.asignacion.node.commons.constants

/**
 * Define constantes referentes a los mensajes de error del sistema.
 */
object SuccessConstants {

  private val CONSTANT_PREFIX = "success."

  lazy val MAX_ASSIGNMENTS_FOR_ROLE_UPDATED: String = Constants.getString("MAX_ASSIGNMENTS_FOR_ROLE_UPDATED", CONSTANT_PREFIX)
  lazy val WORKER_CREATED: String = Constants.getString("WORKER_CREATED", CONSTANT_PREFIX)
  lazy val WORKER_DISABLED: String = Constants.getString("WORKER_DISABLED", CONSTANT_PREFIX)
  lazy val WORKER_ENABLED: String = Constants.getString("WORKER_ENABLED", CONSTANT_PREFIX)
  lazy val WORKER_DELETED: String = Constants.getString("WORKER_DELETED", CONSTANT_PREFIX)
  lazy val WORKER_TRANSFERRED: String = Constants.getString("WORKER_TRANSFERRED", CONSTANT_PREFIX)
  lazy val ASSIGNMENTS_TRANSFERRED: String = Constants.getString("ASSIGNMENTS_TRANSFERRED", CONSTANT_PREFIX)
  lazy val ASSIGNMENTS_UNASSIGNED: String = Constants.getString("ASSIGNMENTS_UNASSIGNED", CONSTANT_PREFIX)
  lazy val WORKER_ROLE_CHANGED: String = Constants.getString("WORKER_ROLE_CHANGED", CONSTANT_PREFIX)
}
