package co.com.sura.distribucion.asignacion.node

import com.typesafe.scalalogging.LazyLogging

import co.com.sura.distribucion.asignacion.node.commons.constants.LogConstants
import co.com.sura.distribucion.asignacion.node.core.SystemActors

/**
 * Inicia el sistema.
 *
 * @author Andres Ricardo Peralta Perea.
 */
object Main extends App with LazyLogging {

  logger.debug(LogConstants.NODE_STARTED.concat(s" ${SystemActors.system}"))
}
