/*
 * JsStash.scala
 *
 * Updated: Apr 20, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import jsactor.JsInternalActorContext.Envelope

/**
 * @author steven
 *
 */
trait JsStash extends JsActor {
  private var theStash = Vector.empty[Envelope]

  private def _ctx = context match {
    case iac: JsInternalActorContext ⇒ iac
    case _ ⇒ sys.error("Invalid ActorContext")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    try unstashAll() finally super.preRestart(reason, message)
  }

  override def postStop(): Unit = try unstashAll() finally super.postStop()

  def stash(): Unit = {
    val currMsg = _ctx._currentMessage
    if (theStash.nonEmpty && (currMsg eq theStash.last))
      throw new IllegalStateException(s"Can't stash the same message $currMsg more than once")

    theStash :+= currMsg
  }

  def unstashAll(): Unit = {
    val ctx = _ctx
    theStash.reverseIterator foreach (e ⇒ ctx._mailbox = e +: ctx._mailbox)
    theStash = Vector.empty
  }
}
