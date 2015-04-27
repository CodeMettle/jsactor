/*
 * JsActorLoggerFactory.scala
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
trait JsActorLoggerFactory {
  def getLogger(name: String): JsActorLogger
}
