package co.s4n.work.distributor.core

import akka.actor.{ ActorRef, ActorSystem, Props }
import com.typesafe.scalalogging.LazyLogging

import co.s4n.work.distributor.commons.constants.Constants
import co.s4n.work.distributor.country.aggregate.Country

/**
 * Inicializa los componentes básicos del sistema, ambiente de ejecución, componente para
 * carga de archivos de propiedades y actor principal del sistema.
 *
 * @author Andres Ricardo Peralta Perea.
 */
trait BootedCore extends Core with LazyLogging {

  val system: ActorSystem = ActorSystem("Sistema", Constants.config)

  sys.addShutdownHook(system.shutdown())
}

/**
 * Inicia los actores principales del sistema.
 *
 * @author Andres Ricardo Peralta Perea.
 */
trait CoreActors { this: Core =>

  protected val country: ActorRef = system.actorOf(Props[Country], "pais")
}

/**
 * Define los componentes básicos del sistema.
 *
 * @author Andres Ricardo Peralta Perea.
 */
trait Core {

  def system: ActorSystem
}
