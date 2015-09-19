package co.com.sura.distribucion.asignacion.node.workers.aggregate.gatherers

import akka.actor.{ ActorRef, Props }

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ RoleMaxAssignments, NotMe, Gatherer, GathererObject }
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.roles.aggregate.RolesInfo.GiveMaxAssignments
import co.com.sura.distribucion.asignacion.node.workers.aggregate.Worker.ChangeRoleMsg

/**
 * Creado por andress4n on 27/11/14.
 */
object ChangeRoleWorkerGatherer extends GathererObject {

  /**
   * Retorna Props del actor [[CreateWorkerGatherer]].
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(ChangeRoleWorkerGatherer(listener, expectedResponses = 1, maxResponses, request.asInstanceOf[ChangeWorkerRoleRQ]))
  }
}

case class ChangeRoleWorkerGatherer(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: ChangeWorkerRoleRQ) extends Gatherer {

  val defaultResponse = WorkerNotExists

  /**
   * Maneja los mensajes recibidos por el actor.
   */
  def receive: Receive = {
    case WorkerExists => sendToRolesInfo(GiveMaxAssignments(request.rol))
    case RoleUndefined => sendFinalResponse(RoleUndefined)
    case RoleMaxAssignments(max) => sendToWorker(ChangeRoleMsg(request.login, request.rol), request.login)
    case ChangeRoleSuccessfully => sendFinalResponse(ChangeRoleSuccessfully)
    case NotMe => // Ignorar
    case msg => printUnknownMsg(msg)
  }
}
