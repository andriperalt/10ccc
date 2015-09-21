package co.s4n.work.distributor.country.aggregate

import scala.collection.JavaConverters._

import co.s4n.work.distributor.cities.aggregate.City
import co.s4n.work.distributor.commons.aggregate.CommonActor
import co.s4n.work.distributor.commons.constants.Constants
import co.s4n.work.distributor.commons.exceptions.TechnicalException
import co.s4n.work.distributor.commons.util.StringUtils._
import co.s4n.work.distributor.commons.ws._
import co.s4n.work.distributor.country.aggregate.Country._

/**
 * Define los objetos y funciones auxiliares usadas por el actor.
 */
object Country {

  private[Country] case class CountryState(cities: Map[String, CityInfo] = Map.empty[String, CityInfo]) {

    override def toString: String = {
      val builder = new StringBuilder().append("Ciudades:\n")
      for {
        (cityCode, cityData) <- cities
      } yield builder.append(s"Código: $cityCode\n").append(s"\tDatos:\n").append(s"\t$cityData\n")
      builder.toString()
    }
  }

  private[Country] case class CityInfo(cityName: String, work: Int = 0) {

    override def toString: String = {
      new StringBuilder().append(s"\tNombre: $cityName\n").append(s"\t\t\t\tCantidad de trabajo actual: $work\n").toString()
    }
  }

  case object CityNotExists extends Response

  case class CountryUpdated(countryCode: String, work: Int) extends Response

}

/**
 * Red del país.
 */
case class Country() extends CommonActor {

  private[Country] var state = CountryState()

  override def preStart(): Unit = {
    logState()
  }

  override def postStop(): Unit = sys.exit()

  def receive: Receive = {
    case msg: Any =>
    case msg => printUnknownMsg(msg)
  }

  private def addUpdateCity(cityCode: String, data: CityInfo) = state = state.copy(cities = state.cities + (cityCode -> data))

  private def logState() = printState(state)
}
