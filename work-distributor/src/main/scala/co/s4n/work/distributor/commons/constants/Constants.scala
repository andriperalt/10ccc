package co.com.sura.distribucion.asignacion.node.commons.constants

import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

/**
 * Constantes - Objeto que permite cargar las constantes de la aplicación del archivo de configuración application.conf
 * ubicado en la carpeta resources.
 */
object Constants {

  val config: Config = ConfigFactory.load()

  private val PREFIX = "sura.util.constants."

  def getString(key: String, constantPrefix: String): String = config.getString(concat(key, constantPrefix))

  def getInteger(key: String, constantPrefix: String): Int = config.getInt(concat(key, constantPrefix))

  def getStringList(key: String, constantPrefix: String): Vector[String] = config.getStringList(concat(key, constantPrefix)).asScala.toVector

  def getDuration(key: String, constantPrefix: String): Duration = Duration(getString(key, constantPrefix))

  private def concat(key: String, constantPrefix: String) = PREFIX.concat(constantPrefix).concat(key)
}
