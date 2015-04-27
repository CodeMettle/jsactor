/*
 * JsEventStream.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import jsactor.logging.JsActorLoggerFactory
import scala.concurrent.ExecutionContextExecutor

/**
 * @author steven
 *
 */
class JsEventStream(logFactory: JsActorLoggerFactory)(implicit dispatcher: ExecutionContextExecutor) {
  private val log = logFactory.getLogger(getClass.getName)

  private var subscribers = Map.empty[Class[_], Set[JsActorRef]]

  private[jsactor] def actorTerminated(actor: JsActorRef) = {
    log.trace(s"$actor terminated subscribers=$subscribers")
    subscribers = (Map.empty[Class[_], Set[JsActorRef]] /: subscribers) {
      case (acc, (clazz, subs)) if subs(actor) ⇒
        val newSubs = subs - actor
        if (newSubs.nonEmpty)
          acc + (clazz → newSubs)
        else
          acc

      case (acc, e) ⇒ acc + e
    }
    log.trace(s"subscribers=$subscribers")
  }

  def subscribe(subscriber: JsActorRef, channel: Class[_]) = {
    log.trace(s"subscribing $subscriber to $channel with subscribers=$subscribers")
    val newSubs = subscribers.getOrElse(channel, Set.empty) + subscriber
    subscribers += (channel → newSubs)
    log.trace(s"subscribers now=$subscribers")
  }

  def unsubscribe(subscriber: JsActorRef) = actorTerminated(subscriber)

  def unsubscribe(subscriber: JsActorRef, channel: Class[_]) = {
    subscribers get channel match {
      case None ⇒
      case Some(subs) ⇒
        subscribers += (channel → (subs + subscriber))
    }
  }

  def publish(event: Any): Unit = {
    dispatcher.execute(new Runnable {
      override def run(): Unit = {
        log.trace(s"got $event to publish with subscribers=$subscribers")
        val evtClass = event.getClass
        subscribers foreach {
          case (subClass, subs) ⇒
            if (subClass isAssignableFrom evtClass)
              subs foreach (_ ! event)
        }
      }
    })
  }
}
