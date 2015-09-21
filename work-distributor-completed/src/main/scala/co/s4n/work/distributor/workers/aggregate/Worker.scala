package co.s4n.work.distributor.workers.aggregate

import akka.actor.Props

import co.s4n.work.distributor.branches.aggregate.Branch.WorkerUpdated
import co.s4n.work.distributor.commons.aggregate.CommonActor
import co.s4n.work.distributor.commons.ws.{ AssignWorkerRQ, Response }
import co.s4n.work.distributor.workers.aggregate.Worker._

/**
 * Fábrica de instancias del actor.
 * Define los objetos y funciones auxiliares usadas por el actor.
 */
object Worker {

  def props(ID: String, maxWork: Int): Props = Props(Worker(ID, maxWork))

  case class WorkerState(maxWork: Int, work: Int = 0) {

    override def toString: String = {
      new StringBuilder().append(s"Máxima cantidad de trabajo permitida: $maxWork\n").append(s"Cantidad de trabajo actual: $work\n") toString ()
    }
  }

  case class MaxWorkExceeded(maxWork: Int, work: Int) extends Response

  case class WorkAssigned(workerID: String) extends Response

}

/**
 * Un trabajador del sistema.
 */
case class Worker(id: String, maxWork: Int) extends CommonActor {

  private[Worker] var state = WorkerState(maxWork = maxWork)

  def receive: Receive = {
    case msg: AssignWorkerRQ => process(msg)
    case msg => printUnknownMsg(msg)
  }

  private def process(msg: AssignWorkerRQ) = {
    val newWork = msg.work + state.work
    if (newWork > state.maxWork) {
      sender() ! MaxWorkExceeded(state.maxWork, state.work)
    } else {
      updateWork(newWork)
      logState()
      sender() ! WorkAssigned(id)
      updateBranch()
    }
  }

  private def updateWork(work: Int) = state = state.copy(work = work)

  private def updateBranch() = context.parent ! WorkerUpdated(id, state.work)

  private def logState() = printState(state)
}
