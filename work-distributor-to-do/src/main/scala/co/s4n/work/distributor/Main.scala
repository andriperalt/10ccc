package co.s4n.work.distributor

import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration._

import co.s4n.work.distributor.branches.aggregate.Branch.WorkerCreated
import co.s4n.work.distributor.commons.ws.{ Request, AssignWorkerRQ, CreateWorkerRQ }
import co.s4n.work.distributor.core.{ BootedCore, CoreActors }
import co.s4n.work.distributor.commons.util.StringUtils._
import co.s4n.work.distributor.workers.aggregate.Worker.WorkAssigned

/**
 * Inicia el sistema.
 *
 * @author Andres Ricardo Peralta Perea.
 */
object Main extends BootedCore with CoreActors with App with LazyLogging {

  private val duration: FiniteDuration = 5.seconds
  private implicit val timeout: Timeout = Timeout(duration)

  // Se espera que lea los archivos de propiedades y se creen los actores
  Thread.sleep(3000)

  var rq: Request = CreateWorkerRQ("c1".normalized, "b1".normalized, "worker1".normalized)
  var rsp = Await.result(country ? rq, duration)
  println(rsp)
  assert(rsp.toString == WorkerCreated().toString)

  rq = AssignWorkerRQ("c1".normalized, "b1".normalized, work = 10)
  rsp = Await.result(country ? rq, duration)
  println(rsp)
  assert(rsp.toString == WorkAssigned("worker1".normalized).toString)

  rq = CreateWorkerRQ("c1".normalized, "b1".normalized, "worker2".normalized)
  rsp = Await.result(country ? rq, duration)
  println(rsp)
  assert(rsp.toString == WorkerCreated().toString)

  rq = AssignWorkerRQ("c1".normalized, "b1".normalized, work = 10)
  rsp = Await.result(country ? rq, duration)
  println(rsp)
  assert(rsp.toString == WorkAssigned("worker2".normalized).toString)

  sys.exit()
}
