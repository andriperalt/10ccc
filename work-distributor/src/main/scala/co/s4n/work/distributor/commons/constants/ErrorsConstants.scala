package co.com.sura.distribucion.asignacion.node.commons.constants

/**
 * Define constantes referentes a los mensajes de error del sistema.
 */
object ErrorsConstants {

  private val CONSTANT_PREFIX = "errors."

  lazy val REPEATED_ROLE_FOUND: String = Constants.getString("REPEATED_ROLE_FOUND", CONSTANT_PREFIX)
  lazy val REPEATED_ROLE_PROCESS_FOUND: String = Constants.getString("REPEATED_ROLE_PROCESS_FOUND", CONSTANT_PREFIX)
  lazy val ASSIGN_ALGORITHM_NOT_FOUND: String = Constants.getString("ASSIGN_ALGORITHM_NOT_FOUND", CONSTANT_PREFIX)
  lazy val UNEXPECTED_ERROR: String = Constants.getString("UNEXPECTED_ERROR", CONSTANT_PREFIX)
  lazy val INVALID_DATE_FORMAT: String = Constants.getString("INVALID_DATE_FORMAT", CONSTANT_PREFIX)
  lazy val INVALID_SERVICE_EXCEPTION_FORMAT: String = Constants.getString("INVALID_SERVICE_EXCEPTION_FORMAT", CONSTANT_PREFIX)
  lazy val PARTIAL_STARTED: String = Constants.getString("PARTIAL_STARTED", CONSTANT_PREFIX)
  lazy val INVALID_MAX_ASSIGNMENTS_FOR_ROLE: String = Constants.getString("INVALID_MAX_ASSIGNMENTS_FOR_ROLE", CONSTANT_PREFIX)
  lazy val ROLE_DOESNT_EXIST: String = Constants.getString("ROLE_DOESNT_EXIST", CONSTANT_PREFIX)
  lazy val OFFICE_DOESNT_EXIST: String = Constants.getString("OFFICE_DOESNT_EXIST", CONSTANT_PREFIX)
  lazy val WORKER_ALREADY_CREATED: String = Constants.getString("WORKER_ALREADY_CREATED", CONSTANT_PREFIX)
  lazy val INVALID_ASSIGNMENTS: String = Constants.getString("INVALID_ASSIGNMENTS", CONSTANT_PREFIX)
  lazy val INVALID_PROCESS_CODE: String = Constants.getString("INVALID_PROCESS_CODE", CONSTANT_PREFIX)
  lazy val PROCESS_NOT_IN_ROL: String = Constants.getString("PROCESS_NOT_IN_ROL", CONSTANT_PREFIX)
  lazy val NOT_WORKER_TO_ASSIGN: String = Constants.getString("NOT_WORKER_TO_ASSIGN", CONSTANT_PREFIX)
  lazy val INVALID_SELF_ENABLING_DATE: String = Constants.getString("INVALID_SELF_ENABLING_DATE", CONSTANT_PREFIX)
  lazy val WORKER_DOESNT_EXIST: String = Constants.getString("WORKER_DOESNT_EXIST", CONSTANT_PREFIX)
  lazy val WORKER_HAS_ASSIGNMENTS: String = Constants.getString("WORKER_HAS_ASSIGNMENTS", CONSTANT_PREFIX)
  lazy val WORKER_IS_DISABLE: String = Constants.getString("WORKER_IS_DISABLE", CONSTANT_PREFIX)
  lazy val WORKER_IS_ENABLE: String = Constants.getString("WORKER_IS_ENABLE", CONSTANT_PREFIX)
  lazy val WORKER_ALREADY_IN_OFFICE: String = Constants.getString("WORKER_ALREADY_IN_OFFICE", CONSTANT_PREFIX)
  lazy val SAME_SOURCE_DESTINATION_WORKER: String = Constants.getString("SAME_SOURCE_DESTINATION_WORKER", CONSTANT_PREFIX)
  lazy val WORKERS_DOESNT_EXIST: String = Constants.getString("WORKERS_DOESNT_EXIST", CONSTANT_PREFIX)
  lazy val WORKER_LOGIN_DOESNT_EXIST: String = Constants.getString("WORKER_LOGIN_DOESNT_EXIST", CONSTANT_PREFIX)
  lazy val SOURCE_WORKER_NOT_ENOUGH_ASSIGNMENTS: String = Constants.getString("SOURCE_WORKER_NOT_ENOUGH_ASSIGNMENTS", CONSTANT_PREFIX)
  lazy val WORKERS_HAVE_DIFFERENT_ROLES: String = Constants.getString("WORKERS_HAVE_DIFFERENT_ROLES", CONSTANT_PREFIX)
  lazy val DESTINATION_WORKER_INVALID_MAX_ASSIGNMENTS: String = Constants.getString("DESTINATION_WORKER_INVALID_MAX_ASSIGNMENTS", CONSTANT_PREFIX)
  lazy val WORKER_NOT_ENOUGH_ASSIGNMENTS: String = Constants.getString("WORKER_NOT_ENOUGH_ASSIGNMENTS", CONSTANT_PREFIX)
}
