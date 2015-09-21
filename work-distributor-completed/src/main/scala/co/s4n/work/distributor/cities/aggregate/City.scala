package co.s4n.work.distributor.cities.aggregate

import akka.actor._
import com.typesafe.config.Config

import scala.collection.JavaConverters._

import co.s4n.work.distributor.branches.aggregate.Branch
import co.s4n.work.distributor.cities.aggregate.City._
import co.s4n.work.distributor.commons.aggregate.CommonActor
import co.s4n.work.distributor.commons.exceptions.TechnicalException
import co.s4n.work.distributor.commons.util.StringUtils._
import co.s4n.work.distributor.commons.ws.{ AssignWorkerRQ, CreateWorkerRQ, Response }
import co.s4n.work.distributor.country.aggregate.Country.CountryUpdated

/**
 * Fábrica de instancias del actor.
 * Define los objetos y funciones auxiliares usadas por el actor.
 */
object City {

  def props(name: String, code: String, config: Config): Props = Props(City(name, code, config))

  private[City] case class CityState(work: Int = 0, branches: Map[String, BranchInfo] = Map.empty[String, BranchInfo]) {

    override def toString: String = {
      val builder = new StringBuilder().append(s"Cantidad de trabajo actual: $work\n").append("Sucursales:\n")
      for {
        (branchCode, branchInfo) <- branches
      } yield builder.append(s"Código: $branchCode\n").append(s"\tDatos:\n").append(s"\t$branchInfo\n")
      builder.toString()
    }
  }

  private[City] case class BranchInfo(branchName: String, work: Int = 0) {

    override def toString: String = new StringBuilder().append(s"\tNombre: $branchName\n").append(s"\t\t\t\tCantidad de trabajo actual: $work\n").toString()
  }

  case object BranchNotExists extends Response

  case class BranchUpdated(branchCode: String, work: Int) extends Response

}

/**
 * Una ciudad del país.
 */
case class City(name: String, code: String, config: Config) extends CommonActor {

  private[City] var state = CityState()

  override def postStop(): Unit = sys.exit()

  override def preStart() {
    loadCityInfo()
    logState()
  }

  def receive: Receive = {
    case msg: CreateWorkerRQ => process(msg)
    case msg: AssignWorkerRQ => process(msg)
    case msg: BranchUpdated => process(msg)
    case msg => printUnknownMsg(msg)
  }

  private def process(msg: CreateWorkerRQ) = forwardToBranch(msg.branchCode, msg)

  private def process(msg: AssignWorkerRQ) = forwardToBranch(msg.branchCode, msg)

  private def process(msg: BranchUpdated) = {
    for {
      branchInfo <- state.branches.get(msg.branchCode)
    } yield {
      val newBranchInfo = branchInfo.copy(work = msg.work)
      addUpdateBranch(msg.branchCode, newBranchInfo)
      val oldWork = branchInfo.work
      val newWork = state.work - oldWork + msg.work
      state = state.copy(work = newWork)
      logState()
      context.parent ! CountryUpdated(code, state.work)
    }
  }

  private def forwardToBranch(branchCode: String, msg: Any) = context.child(branchCode) match {
    case None => sender() ! BranchNotExists
    case Some(branch) => branch forward msg
  }

  private def loadCityInfo() = {
    val branches = config.getConfigList("branches").asScala
    if (branches.isEmpty) {
      throw TechnicalException("No hay sucursales definidas.")
    } else {
      for {
        branchItem <- branches
      } yield {
        val branchConf = branchItem.getConfig("branch")
        val branchName = branchConf.getString("name").normalized
        val branchCode = branchConf.getString("code").normalized
        createChild(Branch.props(branchName, branchCode), branchCode, "Sucursal").get
        addUpdateBranch(branchCode, BranchInfo(branchName))
      }
    }
  }

  private def addUpdateBranch(branchCode: String, data: BranchInfo) = state = state.copy(branches = state.branches + (branchCode -> data))

  private def logState() = printState(state)
}
