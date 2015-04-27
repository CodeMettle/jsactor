/*
 * JsActorLogging.scala
 *
 * Updated: Apr 27, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.logging

import jsactor.JsActor

/**
 * @author steven
 *
 */
trait JsActorLogging {
  self: JsActor ⇒

  protected lazy val log = context.system.getLogger(getClass.getName)
}
