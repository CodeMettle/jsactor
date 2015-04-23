/*
 * package.scala
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
package object pattern {
  import scala.language.implicitConversions

  implicit def ask(a: JsActorRef): JsAskableActorRef = new JsAskableActorRef(a)
}
