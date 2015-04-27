/*
 * JsActorLogger.scala
 *
 * Updated: Apr 27, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.logging

/**
 * @author steven
 *
 */
trait JsActorLogger {
  def trace(msg: Any, addl: Any*): Unit
  def debug(msg: Any, addl: Any*): Unit
  def info(msg: Any, addl: Any*): Unit
  def warn(msg: Any, addl: Any*): Unit
  def error(msg: Any, addl: Any*): Unit
}
