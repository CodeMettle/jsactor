/*
 * JsNullActorLoggerFactory.scala
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
object JsNullActorLoggerFactory extends JsActorLoggerFactory {
  private val nullLogger = new JsActorLogger {
    override def warn(msg: Any, addl: Any*): Unit = {}

    override def error(msg: Any, addl: Any*): Unit = {}

    override def debug(msg: Any, addl: Any*): Unit = {}

    override def trace(msg: Any, addl: Any*): Unit = {}

    override def info(msg: Any, addl: Any*): Unit = {}
  }

  override def getLogger(name: String): JsActorLogger = nullLogger
}
