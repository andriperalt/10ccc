package co.com.sura.distribucion.asignacion.node.roles.aggregate.gatherers

import akka.actor.{ ActorRef, Props }

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.aggregate.{ NotMe, MaxAssignmentsByRole, Gatherer, GathererObject }
import co.com.sura.distribucion.asignacion.node.commons.ws._
import co.com.sura.distribucion.asignacion.node.roles.aggregate.RolesInfo.GiveMaxAssignmentsByRole

/**
 * Fábrica de instancias de [[GiveInfoRedGatherer]].
 * Define los mensajes específicos involucrados en la recolección de información de la red.
 */
object GiveInfoRedGatherer extends GathererObject {

  /**
   * Retorna Props del actor [[GiveInfoRedGatherer]].
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props = {
    Props(GiveInfoRedGatherer(listener, maxResponses, maxResponses, request.asInstanceOf[GiveInfoRedRQ]))
  }
}

/**
 * Acumula las posibles respuestas al comando de recolección de información de la red y genera una respuesta final.
 *
 * Pasos:
 * 1. Le pide la información de las regionales.
 * 2. Le pide la información de los roles.
 *
 * Posibles respuestas:
 * [[OfficeNotExists]]
 * - Si no existen oficinas en la red.
 * [[InfoRed]]
 * - Con la información de la red .
 */
case class GiveInfoRedGatherer(listener: ActorRef, expectedResponses: Int, maxResponses: Int, request: GiveInfoRedRQ) extends Gatherer {

  val defaultResponse = OfficeNotExists

  private var responsesOK = Seq.empty[InfoRegional]
  private var reducedRegionals = Seq.empty[InfoRegional]
  private var mappedRoles = Seq.empty[InfoRol]

  /**
   * Maneja los mensajes recibidos por los actores que son en los que el gatherer esta interesado.
   */
  def receive: Receive = {
    case response: InfoRegional =>
      responsesOK = responsesOK :+ response
      reviewAllResponses()

    case response: MaxAssignmentsByRole =>
      mapRoles(response.roles)
      sendFinalResponse(InfoRed(mappedRoles, reducedRegionals))

    case NotMe => // Ignorar
    case msg => printUnknownMsg(msg)
  }

  /**
   * Revisa si ya se recibieron todas las respuestas de las regionales para procesarlas.
   */
  private def reviewAllResponses() = {
    if (responsesOK.size == responses) {
      reduceRegionals()
      sendToRolesInfo(GiveMaxAssignmentsByRole)
    }
  }

  /**
   * Reduce las respuestas de las regionales eliminando la información redundante.
   */
  private def reduceRegionals() = {
    val toReduce = groupMapRegionals()
    reducedRegionals = toReduce.map(createRegional).toList.sortWith(_.codigo < _.codigo)
  }

  /**
   * Crea la información de la regional para se enviada.
   */
  private def createRegional(data: ((String, String, String), Seq[InfoOficina])) = {
    val (info, offices) = data
    val (code, name, percentage) = info
    InfoRegional(code, name, percentage, offices.sortWith(_.codigo < _.codigo))
  }

  /**
   * Agrupa y mapea la lista de regionales.
   */
  private def groupMapRegionals() = responsesOK.groupBy(groupRegionals).mapValues(mapRegionals)

  private def groupRegionals(info: InfoRegional) = (info.codigo, info.nombre, info.porcentajeOcupacion)

  private def mapRegionals(regionals: Seq[InfoRegional]) = regionals.map(x => x.oficinas.head)

  /**
   * Mapeo de la respuesta del manejador de la información de los roles del sistema a la respuesta del servicio.
   */
  private def mapRoles(roles: Map[String, Int]) = {
    mappedRoles = roles.toList.map {
      case (name, maxAssignments) => InfoRol(name, maxAssignments)
    }.sortWith(_.nombre < _.nombre)
  }
}
