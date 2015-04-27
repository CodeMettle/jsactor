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
    override def warn(msg: Any*): Unit = println("[warn] " + msg.mkString(" "))

    override def error(msg: Any*): Unit = println("[error] " + msg.mkString(" "))

    override def debug(msg: Any*): Unit = println("[debug] " + msg.mkString(" "))

    override def trace(msg: Any*): Unit = println("[trace] " + msg.mkString(" "))

    override def info(msg: Any*): Unit = println("[info] " + msg.mkString(" "))
  }

  override def getLogger(name: String): JsActorLogger = printlnLogger
}
