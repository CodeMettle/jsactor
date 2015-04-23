/*
 * JsProps.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

/**
 * @author steven
 *
 */
class JsProps(creator: ⇒ JsActor) {
  private[jsactor] def newActor(): JsActor = creator
}

object JsProps {
  def apply[T <: JsActor](creator: ⇒ T) = new JsProps(creator)
}
