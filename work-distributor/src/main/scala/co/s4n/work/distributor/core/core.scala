package co.com.sura.distribucion.asignacion.node.core

import akka.actor.{ ActorPath, ActorSystem, Props }
import akka.routing.FromConfig
import com.typesafe.scalalogging.LazyLogging

import co.com.sura.distribucion.asignacion.common.cluster.NodeMember
import co.com.sura.distribucion.asignacion.node.commons.constants.Constants
import co.com.sura.distribucion.asignacion.node.gatherer.aggregate.GathererBuilder
import co.com.sura.distribucion.asignacion.node.offices.aggregate.OfficesSupervisor
import co.com.sura.distribucion.asignacion.node.red.aggregate.Red
import co.com.sura.distribucion.asignacion.node.roles.aggregate.RolesInfoSupervisor
import co.com.sura.distribucion.asignacion.node.workers.aggregate.WorkersSupervisor

/**
 * Sistema de actores del sistema
 */
object SystemActors extends BootedCore with CoreActors

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

  private val rolesInfoSupervisor = system.actorOf(Props[RolesInfoSupervisor], "roles-info-supervisor")
  private val red = system.actorOf(Props[Red], "red")
  private val officesSupervisor = system.actorOf(Props[OfficesSupervisor], "offices-supervisor")
  private val workersSupervisor = system.actorOf(Props[WorkersSupervisor], "workers-supervisor")
  private val gathererBuilder = system.actorOf(Props[GathererBuilder], "gatherer-builder")

  var rolesInfoSupervisorPath: ActorPath = rolesInfoSupervisor.path
  var redPath: ActorPath = red.path
  var officesSupervisorPath: ActorPath = officesSupervisor.path
  var workersSupervisorPath: ActorPath = workersSupervisor.path
  var gathererBuilderPath: ActorPath = gathererBuilder.path
}

/**
 * Define los componentes básicos del sistema.
 *
 * @author Andres Ricardo Peralta Perea.
 */
trait Core {

  def system: ActorSystem
}
