package co.s4n.work.distributor.commons.ws

case class CreateWorkerRQ(cityCode: String, branchCode: String, workerID: String) extends Request

case class AssignWorkerRQ(cityCode: String, branchCode: String, work: Int) extends Request {

  require(work > 0, "Número de solicitudes inválido.")
}

case class DeleteWorkerRQ(workerID: String) extends Request

trait Request extends Serializable
