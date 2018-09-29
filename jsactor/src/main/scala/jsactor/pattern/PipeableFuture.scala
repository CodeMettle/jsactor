/*
 * PipeableFuture.scala
 *
 * Updated: Sep 29, 2018
 *
 * Copyright (c) 2018, CodeMettle
 */
package jsactor.pattern

import jsactor.{JsActor, JsActorRef, JsStatus}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * @author steven
  *
  */
final class PipeableFuture[T](val future: Future[T])(implicit ec: ExecutionContext) {
  def pipeTo(recip: JsActorRef)(implicit sender: JsActorRef = JsActor.noSender): Future[T] = {
    future andThen {
      case Success(r) ⇒ recip ! r
      case Failure(f) ⇒ recip ! JsStatus.Failure(f)
    }
  }
}
