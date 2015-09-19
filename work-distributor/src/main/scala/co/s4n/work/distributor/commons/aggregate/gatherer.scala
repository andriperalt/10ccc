package co.com.sura.distribucion.asignacion.node.commons.aggregate

import akka.actor._

import co.com.sura.distribucion.asignacion.common.deff.ws._
import co.com.sura.distribucion.asignacion.node.commons.ws.{ Response, Request }

/**
 * Define un objeto acompañante de un gatherer.
 *
 * @author Andres Ricardo Peralta Perea.
 */
trait GathererObject extends Serializable {

  /**
   * Retorna una instancia [[Props]] del actor gatherer.
   *
   * @param listener Actor del servicio al que se le responderá finalmente.
   * @param maxResponses Máximo número de respuestas totales que se pueden llegar a recibir.
   * @param request Solicitud original que llega del servicio.
   */
  def props(listener: ActorRef, maxResponses: Int, request: Request): Props
}

/**
 * Define funciones comunes entre los gatherers de los servicios del sistema.
 *
 * @author Andres Ricardo Peralta Perea.
 */
trait Gatherer extends CommonActor with Stash {

  /**
   * Listener Actor al que se le responderá.
   */
  val listener: ActorRef

  /**
   * Número de respuestas posiblemente validas necesarias para intentar responder.
   */
  val expectedResponses: Int

  /**
   * Máximo número de respuestas totales que se pueden llegar a recibir.
   */
  val maxResponses: Int

  /**
   * Request original que llega al gatherer.
   */
  val request: Request

  /**
   * Respuesta que se envía al listener si no hay respuestas que esperar.
   */
  val defaultResponse: Response

  /**
   * Número de respuestas [[NotMe]] recibidas.
   */
  var notMeResponses: Int = 0

  /**
   * Número de respuestas diferentes a [[NotMe]] recibidas.
   */
  var responses: Int = 0

  /**
   * Override para iniciar la recepción de las respuestas de los actores en los que no esta interesado el gatherer.
   * Se define como manejar de mensajes entrantes la función [[receiveNotMe]]
   */
  override def preStart(): Unit = if (maxResponses == 0) sendFinalResponse(defaultResponse) else context.become(receiveNotMe)

  /**
   * Envía la respuesta dada al listener.
   * Detiene éste actor.
   *
   * @param response Respuesta que será enviada.
   */
  protected def sendFinalResponse(response: Response): Unit = {
    listener ! response
    context.stop(self)
  }

  /**
   * Maneja los mensajes recibidos por los actores que no son en los que el gatherer esta interesado.
   */
  private def receiveNotMe: Receive = {
    case NotMe =>
      notMeResponses = notMeResponses + 1
      reviewSendDefaultResponseOrContinue()

    case msg =>
      stash()
      responses = responses + 1
      reviewSendDefaultResponseOrContinue()
  }

  /**
   * Revisa si ya se recibieron todas las respuestas y si no se encontraron actores en los que el gatherer éste
   * interesado se envía la respuesta por defecto y se detiene éste actor. De lo contrario se cambia a la función
   * [[receive]].
   */
  private def reviewSendDefaultResponseOrContinue() = {
    if (notMeResponses == maxResponses) {
      sendFinalResponse(defaultResponse)
    } else if (responses == expectedResponses || (responses + notMeResponses) == maxResponses) {
      context.become(receive)
      unstashAll()
    }
  }

  require(expectedResponses > 0, "Expected responses must be greater than 0")
  require(maxResponses > -1, "Max responses must be greater than -1")
}
