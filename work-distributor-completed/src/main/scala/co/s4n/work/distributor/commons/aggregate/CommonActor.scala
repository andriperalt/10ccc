package co.s4n.work.distributor.commons.aggregate

import akka.actor.{ Actor, InvalidActorNameException, Props }
import com.typesafe.scalalogging.LazyLogging

import scala.util.{ Failure, Try }

import co.s4n.work.distributor.commons.exceptions.TechnicalException

/**
 * Define funciones comunes entre los actores del sistema.
 *
 * @author Andres Ricardo Peralta Perea.
 */
trait CommonActor extends Actor with LazyLogging {

  protected val correctedName = self.path.toStringWithoutAddress

  protected def createChild(props: Props, id: String, entity: String) = {
    Try {
      context.actorOf(props, id)
    }.recoverWith {
      case e =>
        // Mensaje puede ser nulo
        val maybeMsg = Option(e.getMessage)
        val maybeGeneratedIfRepeated = for {
          msg <- maybeMsg if isInvalidActorNameException(e) && msg == s"actor name [$id] is not unique!"
        } yield TechnicalException(s"$entity repetido.")
        val generatedE = maybeGeneratedIfRepeated.getOrElse(TechnicalException(s"No se pudo crear $entity.", e))
        Failure(generatedE)
    }
  }

  protected def isInvalidActorNameException(e: Throwable) = e match {
    case _: InvalidActorNameException => true
    case _ => false
  }

  protected def printUnknownMsg(msg: Any) = logger.warn(s"Mensaje inesperado recibido por el actor [[$correctedName]]: [[$msg]]")

  protected def printState(state: Any) = logger.debug(s"Estado actual del actor [[$correctedName}]]:\n$state")
}
