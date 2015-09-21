package co.s4n.work.distributor.commons.exceptions

object BusinessException {

  def apply(msg: String, cause: Throwable): BusinessException = {
    val e = BusinessException(msg)
    e.initCause(cause)
    e
  }

}

case class BusinessException(msg: String) extends RuntimeException(msg)
