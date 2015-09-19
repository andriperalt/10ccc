package co.com.sura.distribucion.asignacion.node.roles.aggregate

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{ OneForOneStrategy, Props }

import co.com.sura.distribucion.asignacion.node.commons.constants.LogConstants
import co.com.sura.distribucion.asignacion.node.commons.exceptions.RecoveryStateException
import co.com.sura.distribucion.asignacion.node.commons.aggregate.CommonActor

/**
 * Supervisor del manejador de la información de los roles.
 */
class RolesInfoSupervisor extends CommonActor {

  context.actorOf(Props[RolesInfo], "roles-info")

  /**
   * Procesa los errores producidos por las oficinas.
   */
  override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy(loggingEnabled = false) {
    case e: RecoveryStateException =>
      logger.error(LogConstants.SYSTEM_NOT_RECOVERED.concat(e.message), e)
      Stop
    case e: Throwable =>
      logger.error(LogConstants.SYSTEM_NOT_RECOVERED.concat(Option(e.getMessage).getOrElse(e.toString)), e)
      Stop
  }

  def receive: Receive = {
    case msg => printUnknownMsg(msg)
  }
}
