/*
 * JsPrintlnActorLoggerFactory.scala
 *
 * Updated: Apr 27, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.logging.impl

import jsactor.logging.{JsActorLogger, JsActorLoggerFactory}

/**
 * @author steven
 *
 */
object JsPrintlnActorLoggerFactory extends JsActorLoggerFactory {
  private val printlnLogger = new JsActorLogger {
    private def doLog(prefix: String, msg: Any, addl: Any*) = {
      println(s"[$prefix] $msg" + (if (addl.isEmpty) "" else addl.mkString(" ", " ", "")))
    }

    override def warn(msg: Any, addl: Any*): Unit = doLog("warn", msg, addl)

    override def error(msg: Any, addl: Any*): Unit = doLog("error", msg, addl)

    override def debug(msg: Any, addl: Any*): Unit = doLog("debug", msg, addl)

    override def trace(msg: Any, addl: Any*): Unit = doLog("trace", msg, addl)

    override def info(msg: Any, addl: Any*): Unit = doLog("info", msg, addl)
  }

  override def getLogger(name: String): JsActorLogger = printlnLogger
}
