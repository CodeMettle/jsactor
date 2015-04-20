/*
 * JsUnhandledMessage.scala
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
case class JsUnhandledMessage(message: Any, sender: JsActorRef, receiver: JsActorRef)
