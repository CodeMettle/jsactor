/*
 * JsActor.scala
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
object JsActor {
  type Receive = PartialFunction[Any, Unit]

  @SerialVersionUID(1L)
  object emptyBehavior extends Receive {
    def isDefinedAt(x: Any) = false

    def apply(x: Any) = throw new UnsupportedOperationException("Empty behavior apply()")
  }

  final val noSender: JsActorRef = null
}

trait JsActor {
  type Receive = JsActor.Receive

  implicit val context: JsActorContext = {
    val ctx = JsInternalActorContext.nextContext
    if (ctx == null)
      throw new Exception(s"You cannot create a ${getClass.getSimpleName} directly")

    ctx
  }

  implicit final val self = context.self

  final def sender(): JsActorRef = context.sender()

  def receive: JsActor.Receive

  def preStart(): Unit = ()

  def postStop(): Unit = ()

  def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    context.children foreach { child ⇒
      context.unwatch(child)
      context.stop(child)
    }
    postStop()
  }

  def postRestart(reason: Throwable): Unit = {
    preStart()
  }

  def unhandled(message: Any): Unit = {
    message match {
      case JsTerminated(dead) ⇒ throw new Exception(s"${self.path} did not handle termination of $dead")
      case _ ⇒ context.system.eventStream.publish(JsUnhandledMessage(message, sender(), self))
    }
  }
}
