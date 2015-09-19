package co.com.sura.distribucion.asignacion.node.commons.util

import org.apache.commons.lang3.{ StringUtils => AppacheStringUtils }

/**
 * Define utilidades para [[String]].
 */
object StringUtils {

  implicit class Implicits(val value: String) {

    def normalized: String = AppacheStringUtils.upperCase(AppacheStringUtils.normalizeSpace(value))
  }
}
