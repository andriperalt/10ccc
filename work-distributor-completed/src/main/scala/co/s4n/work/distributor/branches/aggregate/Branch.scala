package co.s4n.work.distributor.branches.aggregate

import akka.actor.Props

import scala.util.Try

import co.s4n.work.distributor.branches.aggregate.Branch._
import co.s4n.work.distributor.cities.aggregate.City.BranchUpdated
import co.s4n.work.distributor.commons.aggregate.CommonActor
import co.s4n.work.distributor.commons.constants.Constants
import co.s4n.work.distributor.commons.ws.{ AssignWorkerRQ, CreateWorkerRQ, Response }
import co.s4n.work.distributor.workers.aggregate.Worker

/**
 * Fábrica de instancias del actor.
 * Define los objetos y funciones auxiliares usadas por el actor.
 */
object Branch {

  def props(name: String, code: String): Props = Props(Branch(name, code))

  private[Branch] case class BranchState(work: Int = 0, workers: Map[String, WorkerInfo] = Map.empty[String, WorkerInfo]) {

    override def toString: String = {
      val builder = new StringBuilder().append(s"Cantidad de trabajo actual: $work\n").append("Trabajadores:\n")
      for {
        (workerID, workerInfo) <- workers
      } yield builder.append(s"\tID: $workerID\n").append(s"\tDatos:\n").append(s"\t$workerInfo\n")
      builder.toString()
    }
  }

  private[Branch] case class WorkerInfo(work: Int = 0) {

    override def toString: String = {
      new StringBuilder().append(s"\t\t\t\tCantidad de trabajo actual: $work\n").toString()
    }
  }

  case object WorkerAlreadyExists extends Response

  case class WorkerNotCreated(error: String) extends Response

  case class WorkerCreated() extends Response

  case object NoWorkers extends Response

  case class WorkerUpdated(workerID: String, work: Int) extends Response

}

/**
 * Una sucursal que pertenece a una ciudad.
 */
case class Branch(name: String, code: String) extends CommonActor {

  private[Branch] var state = BranchState()

  override def postStop(): Unit = sys.exit()

  def receive: Receive = {
    case msg: CreateWorkerRQ => process(msg)
    case msg: AssignWorkerRQ => process(msg)
    case msg: WorkerUpdated => process(msg)
    case msg => printUnknownMsg(msg)
  }

  private def process(msg: CreateWorkerRQ) = {
    val rsp = Try {
      context.actorOf(Worker.props(msg.workerID, Constants.workerMaxWork), msg.workerID)
      addUpdateWorker(msg.workerID, WorkerInfo())
      logState()
      WorkerCreated()
    }.recover {
      case e =>
        // Mensaje puede ser nulo
        val maybeMsg = Option(e.getMessage)
        val maybeGeneratedIfRepeated = for {
          m <- maybeMsg if isInvalidActorNameException(e) && m == s"actor name [${msg.workerID}] is not unique!"
        } yield {
          addUpdateWorker(msg.workerID, WorkerInfo())
          WorkerAlreadyExists
        }
        maybeGeneratedIfRepeated.getOrElse {
          WorkerNotCreated(e.getMessage)
        }
    }.get
    sender() ! rsp
  }

  private def process(msg: AssignWorkerRQ) = {
    if (state.workers.isEmpty) {
      sender() ! NoWorkers
    } else {
      val sorted = state.workers.toSeq.sortBy { case (_, workerInfo) => workerInfo.work }
      val (workerName, _) = sorted.head
      for {
        worker <- context.child(workerName)
      } yield worker forward msg
    }
  }

  private def process(msg: WorkerUpdated) = {
    for {
      workerInfo <- state.workers.get(msg.workerID)
    } yield {
      val newWorkerInfo = workerInfo.copy(work = msg.work)
      addUpdateWorker(msg.workerID, newWorkerInfo)
      val oldWork = workerInfo.work
      val newWork = state.work - oldWork + msg.work
      state = state.copy(work = newWork)
      logState()
      context.parent ! BranchUpdated(code, state.work)
    }
  }

  private def addUpdateWorker(workerName: String, data: WorkerInfo) = state = state.copy(workers = state.workers + (workerName -> data))

  private def logState() = printState(state)
}
