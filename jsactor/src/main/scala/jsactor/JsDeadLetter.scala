/*
 * JsDeadLetter.scala
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
case class JsDeadLetter(message: Any, sender: JsActorRef, recipient: JsActorRef)