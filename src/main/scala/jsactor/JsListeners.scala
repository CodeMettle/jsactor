/*
 * JsListeners.scala
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

sealed trait ListenerMessage
case class Listen(listener: JsActorRef) extends ListenerMessage
case class Deafen(listener: JsActorRef) extends ListenerMessage
case class WithListeners(f: (JsActorRef) ⇒ Unit) extends ListenerMessage

/**
 * Listeners is a generic trait to implement listening capability on an Actor.
 * <p/>
 * Use the <code>gossip(msg)</code> method to have it sent to the listeners.
 * <p/>
 * Send <code>Listen(self)</code> to start listening.
 * <p/>
 * Send <code>Deafen(self)</code> to stop listening.
 * <p/>
 * Send <code>WithListeners(fun)</code> to traverse the current listeners.
 */
trait JsListeners { self: JsActor ⇒
    protected var listeners = Set.empty[JsActorRef]

    /**
     * Chain this into the receive function.
     *
     * {{{ def receive = listenerManagement orElse … }}}
     */
    protected def listenerManagement: JsActor.Receive = {
        case Listen(l) ⇒ listeners += l
        case Deafen(l) ⇒ listeners -= l
        case WithListeners(f) ⇒ listeners foreach f
    }

    /**
     * Sends the supplied message to all current listeners using the provided sender() as sender.
     *
     * @param msg
     * @param sender
     */
    protected def gossip(msg: Any)(implicit sender: JsActorRef = JsActor.noSender): Unit = {
        listeners foreach (_ ! msg)
    }
}
