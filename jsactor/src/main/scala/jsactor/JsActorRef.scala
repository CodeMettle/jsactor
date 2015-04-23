/*
 * JsActorRef.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import java.{lang ⇒ jl}

/**
 * @author steven
 *
 */
object JsActorRef {

  /**
   * Use this value as an argument to [[JsActorRef#tell]] if there is not actor to
   * reply to (e.g. when sending from non-actor code).
   */
  final val noSender: JsActorRef = JsActor.noSender

}

trait JsActorRef extends jl.Comparable[JsActorRef] {
  /**
   * Returns the path for this actor (from this actor up to the root actor).
   */
  def path: JsActorPath

  /**
   * Comparison takes path and the unique id of the actor cell into account.
   */
  override final def compareTo(other: JsActorRef) = {
    this.path compareTo other.path
  }

  /**
   * Sends the specified message to the sender, i.e. fire-and-forget
   * semantics, including the sender reference if possible.
   *
   * Pass [[jsactor.JsActorRef.noSender]] or `null` as sender if there is nobody to reply to
   */
  final def tell(msg: Any, sender: JsActorRef): Unit = this.!(msg)(sender)

  def !(message: Any)(implicit sender: JsActorRef = JsActor.noSender): Unit

  /**
   * Forwards the message and passes the original sender actor as the sender.
   *
   * Works, no matter whether originally sent with tell/'!' or ask/'?'.
   */
  def forward(message: Any)(implicit context: JsActorContext) = tell(message, context.sender())

  final override def hashCode: Int = {
    path.hashCode()
  }

  /**
   * Equals takes path and the unique id of the actor cell into account.
   */
  final override def equals(that: Any): Boolean = that match {
    case other: JsActorRef ⇒ path == other.path
    case _ ⇒ false
  }

  override def toString: String = s"Actor[$path]"
}

private[jsactor] class JsInternalActorRef(override val path: JsActorPath) extends JsActorRef {
  private[jsactor] var ctx: JsInternalActorContext = _

  override def !(message: Any)(implicit sender: JsActorRef): Unit = ctx.receiveMessage(message, sender)
}
