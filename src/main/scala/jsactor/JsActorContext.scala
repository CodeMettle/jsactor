/*
 * JsActorContext.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import com.codemettle.weblogging.WebLogging

import jsactor.JsActor.Receive
import jsactor.JsInternalActorContext.Envelope
import scala.collection.immutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.control.Exception.ultimately
import scala.util.control.NonFatal

/**
 * @author steven
 *
 */
trait JsActorContext extends JsActorRefFactory {
    def children: immutable.Iterable[JsActorRef]

    def child(name: String): Option[JsActorRef]

    def self: JsActorRef

    def sender(): JsActorRef

    def props: JsProps

    def receiveTimeout: Duration

    def setReceiveTimeout(timeout: Duration): Unit

    implicit def dispatcher: ExecutionContextExecutor

    /**
     * Changes the Actor's behavior to become the new 'Receive' (PartialFunction[Any, Unit]) handler.
     * Replaces the current behavior on the top of the behavior stack.
     */
    def become(behavior: JsActor.Receive): Unit = become(behavior, discardOld = true)

    /**
     * Changes the Actor's behavior to become the new 'Receive' (PartialFunction[Any, Unit]) handler.
     * This method acts upon the behavior stack as follows:
     *
     *  - if `discardOld = true` it will replace the top element (i.e. the current behavior)
     *  - if `discardOld = false` it will keep the current behavior and push the given one atop
     *
     * The default of replacing the current behavior on the stack has been chosen to avoid memory
     * leaks in case client code is written without consulting this documentation first (i.e.
     * always pushing new behaviors and never issuing an `unbecome()`)
     */
    def become(behavior: JsActor.Receive, discardOld: Boolean): Unit

    /**
     * Reverts the Actor behavior to the previous one on the behavior stack.
     */
    def unbecome(): Unit

    /**
     * The system that the actor belongs to.
     * Importing this member will place an implicit ActorSystem in scope.
     */
    implicit def system: JsActorSystem

    /**
     * Returns the supervising parent ActorRef.
     */
    def parent: JsActorRef

    /**
     * Registers this actor as a Monitor for the provided ActorRef.
     * This actor will receive a Terminated(subject) message when watched
     * actor is terminated.
     * @return the provided ActorRef
     */
    def watch(subject: JsActorRef): JsActorRef

    /**
     * Unregisters this actor as Monitor for the provided ActorRef.
     * @return the provided ActorRef
     */
    def unwatch(subject: JsActorRef): JsActorRef

    private[jsactor] def stopChild(child: JsActorRef): Unit
}

object JsInternalActorContext {
    private case class Envelope(message: Any, sender: JsActorRef)

    private[jsactor] var nextContext: JsActorContext = _
}

class JsInternalActorContext(override val props: JsProps, override val parent: JsActorRef, _actorRef: JsInternalActorRef)
                            (implicit val system: JsActorSystem, val dispatcher: ExecutionContextExecutor)
    extends JsActorContext with JsInternalActorRefFactory with WebLogging {
    _actorRef.ctx = this

    private var actorImpl: JsActor = _

    private var _behaviorStack = List.empty[JsActor.Receive]

    private def initializeActorImpl(preFunc: (JsActor) ⇒ Unit) = {
        JsInternalActorContext.nextContext = JsInternalActorContext.this
        try {
            actorImpl = props.newActor()
            preFunc(actorImpl)
            if (_behaviorStack.isEmpty)
                _behaviorStack = List(actorImpl.receive)

            // will needlessly re-add on restart, but nbd
            system.actorStarted(self)
        } catch {
            case NonFatal(e) ⇒
                logger.error(s"Error starting ${self.path}: $e")
                actorImpl = null
                setReceiveTimeout(Duration.Undefined)
                _terminated = true

        } finally {
            JsInternalActorContext.nextContext = null
        }
    }

    private var _currentMessage: Envelope = _

    private var _mailbox = Vector.empty[Envelope]

    private var _recvTimeout = Option.empty[(FiniteDuration, JsCancellable)]

    private var _terminated = false
    private var _initialized = false

    override def self: JsActorRef = _actorRef

    override def sender(): JsActorRef = _currentMessage match {
        case null ⇒ system.deadLetters
        case msg if msg.sender ne null ⇒ msg.sender
        case _ ⇒ system.deadLetters
    }

    override def children: immutable.Iterable[JsActorRef] = _children

    override def child(name: String): Option[JsActorRef] = _children find (_.path.name == name)

    override def become(behavior: Receive, discardOld: Boolean): Unit = {
        _behaviorStack = behavior :: (if (discardOld && _behaviorStack.nonEmpty) _behaviorStack.tail else _behaviorStack)
    }

    override def unbecome(): Unit = {
        if (_behaviorStack.nonEmpty)
            _behaviorStack = _behaviorStack.tail
        if (_behaviorStack.isEmpty)
            _behaviorStack = List(actorImpl.receive)
    }

    override def watch(subject: JsActorRef): JsActorRef = {
        system.addWatch(self, subject)
        subject
    }

    override def unwatch(subject: JsActorRef): JsActorRef = {
        system.removeWatch(self, subject)
        subject
    }

    override def receiveTimeout: Duration = _recvTimeout.fold[Duration](Duration.Undefined)(_._1)

    override def setReceiveTimeout(timeout: Duration): Unit = {
        _recvTimeout foreach (_._2.cancel())
        timeout match {
            case fd: FiniteDuration ⇒
                val timer = system.scheduler.scheduleOnce(fd, self, JsReceiveTimeout)
                _recvTimeout = Some(fd → timer)

            case _ ⇒ _recvTimeout = None
        }
    }

    private def deadLetter(msg: Any, sender: JsActorRef) = {
        system.deadLetters ! JsDeadLetter(msg, Option(sender) getOrElse system.deadLetters, self)
    }

    private[jsactor] def start() = {
        initializeActorImpl(_.preStart())
        _initialized = true
        enqueueConsume()
    }

    override private[jsactor] def stopChild(child: JsActorRef): Unit = {
        if (child.path.parent != self.path) sys.error(s"$child is not a child of $self")
        if (_children(child)) {
            ultimately{
                _children -= child
                system.actorStopped(child)
            } apply {
                child.asInstanceOf[JsInternalActorRef].ctx.stop()
            }
        }
    }

    private[jsactor] def stop(): Unit = {
        _initialized = false
        logger.trace(s"stop(); actorImpl = $actorImpl, children = $children, _mailbox.size = ${_mailbox.size}")
        children foreach stopChild
        logger.trace(s"$self is stopping, sending ${_mailbox} to deadLetters")
        _mailbox foreach {
            case Envelope(msg, send) ⇒ deadLetter(msg, send)
        }
        logger.trace(s"calling postStop on $actorImpl")
        try actorImpl.postStop()
        catch {
            case NonFatal(e) ⇒
                logger.error(s"Error while stopping ${self.path}: $e")
        }
        setReceiveTimeout(Duration.Undefined)
        _currentMessage = null
        actorImpl = null
        _terminated = true
    }

    private[jsactor] def receiveMessage(message: Any, sender: JsActorRef) = {
        if (_terminated)
            deadLetter(message, sender)
        else {
            setReceiveTimeout(receiveTimeout)

            val startReceive = _mailbox.isEmpty && _initialized

            _mailbox :+= Envelope(message, sender)

            if (startReceive)
                enqueueConsume()
        }
    }

    private def enqueueConsume(): Unit = {
        dispatcher.execute(new Runnable {
            override def run(): Unit = consume()
        })
    }

    private def restart(reason: Throwable, message: Option[Any]) = {
        _initialized = false
        actorImpl.preRestart(reason, message)
        initializeActorImpl(_.postRestart(reason))
        _initialized = true
        enqueueConsume()
    }

    private def consume(): Unit = if (!_terminated && _initialized && _mailbox.nonEmpty) {
        val m@Envelope(message, _) = _mailbox.head
        _mailbox = _mailbox.tail

        message match {
            case JsPoisonPill ⇒ stop(self)

            case other ⇒
                _currentMessage = m

                try {
                    if (_behaviorStack.head isDefinedAt other) {
                        _behaviorStack.head.apply(other)
                    } else
                        actorImpl.unhandled(other)
                } catch {
                    case NonFatal(e) ⇒
                        _currentMessage = null

                        logger.error(s"$e")
                        logger.error(s"while $self was processing $m")
                        logger.warn(s"Restarting ${self.path}")

                        restart(e, Some(message))
                } finally {
                    _currentMessage = null

                    if (_mailbox.nonEmpty)
                        enqueueConsume()
                }
        }
    }
}
