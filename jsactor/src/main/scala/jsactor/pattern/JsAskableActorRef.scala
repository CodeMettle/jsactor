/*
 * JsAskableActorRef.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.pattern

import jsactor._
import jsactor.logging.JsActorLogging
import scala.concurrent.{Future, Promise}

/**
 * @author steven
 *
 */
private[jsactor] case class JsPromiseActorRef(arf: JsActorRefFactory, timeout: JsTimeout, result: Promise[Any], recip: JsActorRef, msg: Any) {
  val actor = arf.actorOf(JsProps(new JsActor with JsActorLogging {
    val timer = context.system.scheduler.scheduleOnce(timeout.duration, self, JsPoisonPill)

    override def preStart() = {
      super.preStart()

      recip ! msg
    }

    override def postStop() = {
      super.postStop()

      timer.cancel()

      result tryFailure new Exception(s"Timed out after ${timeout.duration} asking $msg of $recip")
    }

    def receive = {
      case JsStatus.Failure(t) ⇒
        result tryFailure t
        context stop self

      case reply ⇒
        log.trace("!! got reply! ", reply)

        result trySuccess reply
        context stop self
    }
  }))
}

final class JsAskableActorRef(val actorRef: JsActorRef) extends AnyVal {

  def ask(message: Any)(implicit timeout: JsTimeout): Future[Any] = actorRef match {
    case ref: JsInternalActorRef ⇒
      if (timeout.duration.length <= 0)
        Future.failed[Any](new IllegalArgumentException(s"Timeout length must not be negative, question not sent to [$actorRef]"))
      else {
        val a = JsPromiseActorRef(ref.ctx.system, timeout, Promise[Any](), actorRef, message)
        a.result.future
      }
    case _ ⇒ Future.failed[Any](new IllegalArgumentException(s"Unsupported recipient ActorRef type, question not sent to [$actorRef]"))
  }

  def ?(message: Any)(implicit timeout: JsTimeout): Future[Any] = ask(message)(timeout)
}
