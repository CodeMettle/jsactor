/*
 * JsStatus.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

/**
 * @author steven
 *
 */
object JsStatus {

  sealed trait JsStatus extends Serializable

  /**
   * This class/message type is preferably used to indicate success of some operation performed.
   */
  @SerialVersionUID(1L)
  case class Success(status: Any) extends JsStatus

  /**
   * This class/message type is preferably used to indicate failure of some operation performed.
   * As an example, it is used to signal failure with AskSupport is used (ask/?).
   */
  @SerialVersionUID(1L)
  case class Failure(cause: Throwable) extends JsStatus

}
