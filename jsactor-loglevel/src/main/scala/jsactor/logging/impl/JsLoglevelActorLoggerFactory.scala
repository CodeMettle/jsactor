/*
 * JsLoglevelActorLoggerFactory.scala
 *
 * Updated: Apr 27, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.logging.impl

import com.github.pimterry.loglevel.LogLevel

import jsactor.logging.{JsActorLogger, JsActorLoggerFactory}

/**
 * @author steven
 *
 */
object JsLoglevelActorLoggerFactory extends JsActorLoggerFactory {
  private val loglevelLogger = new JsActorLogger {
    override def warn(msg: Any, addl: Any*): Unit = LogLevel.log.warn(msg, addl: _*)

    override def error(msg: Any, addl: Any*): Unit = LogLevel.log.error(msg, addl: _*)

    override def debug(msg: Any, addl: Any*): Unit = LogLevel.log.debug(msg, addl: _*)

    override def trace(msg: Any, addl: Any*): Unit = LogLevel.log.trace(msg, addl: _*)

    override def info(msg: Any, addl: Any*): Unit = LogLevel.log.info(msg, addl: _*)
  }

  override def getLogger(name: String): JsActorLogger = loglevelLogger
}
