/*
 * package.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * @author steven
 *
 */
package object pattern {
  final class PipeableFuture[T](val future: Future[T])(implicit ec: ExecutionContext) {
    def pipeTo(recip: JsActorRef)(implicit sender: JsActorRef = JsActor.noSender): Future[T] = {
      future andThen {
        case Success(r) ⇒ recip ! r
        case Failure(f) ⇒ recip ! JsStatus.Failure(f)
      }
    }
  }

  import scala.language.implicitConversions

  implicit def ask(a: JsActorRef): JsAskableActorRef = new JsAskableActorRef(a)
  implicit def pipe[T](f: Future[T])(implicit ec: ExecutionContext): PipeableFuture[T] = new PipeableFuture[T](f)
}
