package co.s4n.work.distributor.commons.exceptions

object TechnicalException {

  def apply(msg: String, cause: Throwable): TechnicalException = {
    val e = TechnicalException(msg)
    e.initCause(cause)
    e
  }

}

case class TechnicalException(msg: String) extends RuntimeException(msg)
