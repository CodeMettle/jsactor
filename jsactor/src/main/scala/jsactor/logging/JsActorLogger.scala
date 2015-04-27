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
  def trace(msg: Any*): Unit
  def debug(msg: Any*): Unit
  def info(msg: Any*): Unit
  def warn(msg: Any*): Unit
  def error(msg: Any*): Unit
}
