/*
 * package.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author steven
 *
 */
package object pattern {
  import scala.language.implicitConversions

  implicit def ask(a: JsActorRef): JsAskableActorRef = new JsAskableActorRef(a)
  implicit def pipe[T](f: Future[T])(implicit ec: ExecutionContext): PipeableFuture[T] = new PipeableFuture[T](f)
}
