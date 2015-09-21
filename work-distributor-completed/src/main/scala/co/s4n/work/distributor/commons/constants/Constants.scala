package co.s4n.work.distributor.commons.constants

import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

/**
 * Constantes - Objeto que permite cargar las constantes de la aplicación del archivo de configuración application.conf
 * ubicado en la carpeta resources.
 */
object Constants {

  val config: Config = ConfigFactory.load()

  lazy val workerMaxWork = config.getInt("workers.max-work")
}
