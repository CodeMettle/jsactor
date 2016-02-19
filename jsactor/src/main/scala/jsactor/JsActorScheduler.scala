/*
 * JsActorScheduler.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js

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

        private var _intervalHandle: js.timers.SetIntervalHandle = _
        private val initialTimer = scheduleOnce(initialDelay) {
                _initialDone = true
                _intervalHandle = js.timers.setInterval(interval)(runnable.run())
                runnable.run()
            }

        override def isCancelled: Boolean = _cancelled

        override def cancel(): Boolean = {
            if (_cancelled)
                false
            else if (_initialDone) {
                js.timers.clearInterval(_intervalHandle)
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

        private val timeoutHandle = js.timers.setTimeout(delay)(runnable.run())

        override def isCancelled: Boolean = _cancelled

        override def cancel(): Boolean = {
            if (_cancelled)
                false
            else {
                js.timers.clearTimeout(timeoutHandle)
                _cancelled = true

                true
            }
        }
    }
}
