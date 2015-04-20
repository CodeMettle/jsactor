/*
 * JsActorScheduler.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import scala.concurrent.duration.FiniteDuration

/**
 * @author steven
 *
 */
trait JsActorScheduler {
    final def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, receiver: JsActorRef, message: Any)
                      (implicit sender: JsActorRef = JsActor.noSender): JsCancellable =
        schedule(initialDelay, interval, new Runnable {
            override def run() = receiver ! message
        })

    final def schedule(initialDelay: FiniteDuration, interval: FiniteDuration)(f: ⇒ Unit): JsCancellable =
        schedule(initialDelay, interval, new Runnable { override def run() = f })

    def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable): JsCancellable

    final def scheduleOnce(delay: FiniteDuration, receiver: JsActorRef, message: Any)
                          (implicit sender: JsActorRef = JsActor.noSender): JsCancellable =
        scheduleOnce(delay, new Runnable {
            override def run() = receiver ! message
        })

    final def scheduleOnce(delay: FiniteDuration)(f: ⇒ Unit): JsCancellable =
        scheduleOnce(delay, new Runnable { override def run() = f })

    def scheduleOnce(delay: FiniteDuration, runnable: Runnable): JsCancellable
}

private[jsactor] class JsActorSchedulerImpl extends JsActorScheduler {
    override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration,
                          runnable: Runnable): JsCancellable = new JsCancellable {
        private var _cancelled = false
        private var _initialDone = false

        private var _intervalHandle: Int = _
        private val initialTimer = scheduleOnce(initialDelay) {
                _initialDone = true
                _intervalHandle = org.scalajs.dom.setInterval(() ⇒ runnable.run(), interval.toMillis.toInt)
                runnable.run()
            }

        override def isCancelled: Boolean = _cancelled

        override def cancel(): Boolean = {
            if (_cancelled)
                false
            else if (_initialDone) {
                org.scalajs.dom.clearInterval(_intervalHandle)
                _cancelled = true

                true
            } else {
                initialTimer.cancel()

                _cancelled = true

                true
            }
        }
    }

    override def scheduleOnce(delay: FiniteDuration, runnable: Runnable): JsCancellable = new JsCancellable {
        private var _cancelled = false

        private val timeoutHandle = org.scalajs.dom.setTimeout(() ⇒ runnable.run(), delay.toMillis.toInt)

        override def isCancelled: Boolean = _cancelled

        override def cancel(): Boolean = {
            if (_cancelled)
                false
            else {
                org.scalajs.dom.clearTimeout(timeoutHandle)
                _cancelled = true

                true
            }
        }
    }
}
