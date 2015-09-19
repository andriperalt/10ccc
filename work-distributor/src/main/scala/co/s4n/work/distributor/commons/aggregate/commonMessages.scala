package co.com.sura.distribucion.asignacion.node.commons.aggregate

import akka.actor.ActorRef

import co.com.sura.distribucion.asignacion.node.commons.ws.Request

/**
 * Respuesta a una solicitud de creación de un gatherer.
 *
 * @param request Solicitud que se atenderá.
 * @param gatherer Gatherer creado.
 */
case class GathererBuilt(request: Request, gatherer: ActorRef)

/**
 * Respuesta a una solicitud que indica el actor no es el que se esta buscando.
 */
case object NotMe

/**
 * Respuesta a una solicitud que indica el trabajador en cuestión existe.
 */
case object OfficeExists

/**
 * Respuesta a una solicitud indica el máximo número de asignaciones de un rol.
 */
case class RoleMaxAssignments(maxAssignments: Int)

/**
 * Respuesta a una solicitud que indica la tabla de trabajadores de un rol dado en una oficina.
 *
 * @param regionalID Código de la regional en la que se debe buscar otros trabajadores candidatos.
 * @param officeID Identificador de la oficina que fue consultada.
 * @param workers Tabla de trabajadores, tupla ( ID del trabajador, número de asignaciones que tiene el trabajador) .
 */
case class WorkersWithCapacity(regionalID: String, officeID: String, workers: Map[String, Int])

/**
 * Respuesta a una solicitud que indica no hay trabajadores en la oficina que cumplan los requisitos buscados.
 *
 * @param regionalID Código de la regional en la que se debe buscar otros trabajadores candidatos.
 */
case class NotWorkersWithCapacity(regionalID: String)

/**
 * Respuesta a una solicitud que indica el trabajador no puede ser asignado porque esta inhabilitado.
 */
case object WorkerCannotBeAssigned

/**
 * Respuesta a una solicitud que indica la tabla de oficinas de un rol dado en una regional.
 *
 * @param offices Tabla de oficinas, tupla ( código de la oficina, número de asignaciones de la oficina) .
 */
case class OfficesWithCapacity(offices: Map[String, Int])

/**
 * Respuesta a una solicitud que indica no hay oficinas en la regional que cumplan los requisitos buscados.
 */
case object NotOfficesWithCapacity

/**
 * Respuesta a una solicitud que indica la tabla de regionales de un rol dado en la red.
 *
 * @param regionals Tabla de regionales, tupla ( código de la regional, número de asignaciones de la regional) .
 */
case class RegionalsWithCapacity(regionals: Map[String, Int])

/**
 * Respuesta a una solicitud que indica no hay regionales en la red que cumplan los requisitos buscados.
 */
case object NotRegionalsWithCapacity

/**
 * Respuesta a una solicitud de recolección del máximo número de asignaciones por rol que indica dicha tabla.
 */
case class MaxAssignmentsByRole(roles: Map[String, Int])

/**
 * Comando que inicia alguna acción en el sistema
 */
trait Command extends Serializable

/**
 * Evento que sucedió en un actor persistente.
 */
trait Event extends Serializable
