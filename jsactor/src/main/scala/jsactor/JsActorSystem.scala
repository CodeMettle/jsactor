/*
 * JsActorSystem.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import jsactor.JsActorSystem._
import jsactor.logging.impl.JsPrintlnActorLoggerFactory
import jsactor.logging.{JsActorLoggerFactory, JsActorLogging}
import scala.concurrent.{ExecutionContextExecutor, Promise}
import scala.scalajs.concurrent.JSExecutionContext

/**
 * @author steven
 *
 */
object JsActorSystem {
  trait Watcher {
    this: JsActorSystem ⇒

    private var watches = Map.empty[JsActorRef, Set[JsActorRef]]

    private var allActors = Set.empty[JsActorRef]

    protected def notifyOfActorStopped(actor: JsActorRef) = {
      watches = (Map.empty[JsActorRef, Set[JsActorRef]] /: watches) {
        case (acc, (watched, watchers)) if watchers(actor) ⇒
          val newWatchers = watchers - actor
          if (newWatchers.nonEmpty)
            acc + (watched → newWatchers)
          else
            acc

        case (acc, e) ⇒ acc + e
      }

      watches get actor foreach (watchers ⇒ {
        val msg = JsTerminated(actor)
        watchers foreach (_ ! msg)
      })

      watches -= actor
    }

    private[jsactor] def addWatch(watcher: JsActorRef, watchee: JsActorRef) = {
      if (allActors contains watchee) {
        val newWatchers = watches.getOrElse(watchee, Set.empty) + watcher
        watches += (watchee → newWatchers)
      } else
        watcher ! JsTerminated(watchee)
    }

    private[jsactor] def removeWatch(watcher: JsActorRef, watchee: JsActorRef) = {
      watches get watchee match {
        case None ⇒
        case Some(watchers) ⇒
          watches += (watchee → (watchers + watcher))
      }
    }

    private[jsactor] def actorStopped(actor: JsActorRef) = {
      eventStream.actorTerminated(actor)
      notifyOfActorStopped(actor)
      allActors -= actor
    }

    private[jsactor] def actorStarted(actor: JsActorRef) = {
      allActors += actor
    }
  }

  private class MinimalActRef(override val path: JsActorPath) extends JsActorRef {
    override def !(message: Any)(implicit sender: JsActorRef): Unit = {
      println(s"Got $message from $sender")
    }
  }

  private class JsActRefProv(name: String, system: JsActorSystem, _dispatcher: ExecutionContextExecutor) extends JsActorRefFactory {
    val rootPath = JsRootActorPath(JsAddress("jsactors", name))

    override implicit def dispatcher: ExecutionContextExecutor = _dispatcher

    private val bubblesSpaceTime = new MinimalActRef(rootPath)

    override def actorOf(props: JsProps): JsActorRef = sys.error("no name not supported on rootctx")

    override def stop(actor: JsActorRef): Unit = sys.error("stop not supported on rootctx")

    override def actorOf(props: JsProps, name: String): JsActorRef = {
      implicit val actSys = system

      val path = rootPath / name

      val newRef = new JsInternalActorRef(path)
      val ctx = new JsInternalActorContext(props, bubblesSpaceTime, newRef)
      ctx.start()
      newRef
    }
  }

  private class UserGuardian(onStop: Promise[Unit]) extends JsActor with JsActorLogging {
    override def postStop() = {
      super.postStop()

      onStop success {}
    }

    def receive = {
      case msg ⇒ log.warn(s"$msg sent to ${self.path}")
    }
  }
}

final case class JsActorSystem(name: String, logFactory: JsActorLoggerFactory = JsPrintlnActorLoggerFactory)
                              (implicit val dispatcher: ExecutionContextExecutor = JSExecutionContext.queue)
  extends JsActorRefFactory with Watcher {
  private val shutdownP = Promise[Unit]()
  def awaitShutdown = shutdownP.future

  val scheduler: JsActorScheduler = new JsActorSchedulerImpl

  val eventStream = new JsEventStream(logFactory)

  private val actRefProv = new JsActRefProv(name, this, dispatcher)

  val deadLetters: JsActorRef = actRefProv.actorOf(JsProps(new JsDeadLettersActor), "deadLetters")

  private val userGuardian: JsActorRef = actRefProv.actorOf(JsProps(new UserGuardian(shutdownP)), "user")

  override def actorOf(props: JsProps): JsActorRef = {
    userGuardian.asInstanceOf[JsInternalActorRef].ctx.actorOf(props)
  }

  override def stop(actor: JsActorRef): Unit = {
    userGuardian.asInstanceOf[JsInternalActorRef].ctx.stop(actor)
  }

  override def actorOf(props: JsProps, name: String): JsActorRef = {
    userGuardian.asInstanceOf[JsInternalActorRef].ctx.actorOf(props, name)
  }

def getLogger(name: String) = logFactory.getLogger(name)

  def shutdown() = {
    dispatcher.execute(new Runnable {
      override def run(): Unit = {
//        logger.trace(s"stopping ${JsActorSystem.this}")
        userGuardian.asInstanceOf[JsInternalActorRef].ctx.stop()
      }
    })
  }
}

