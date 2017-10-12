/*
 * package.scala
 *
 * Updated: Oct 12, 2017
 *
 * Copyright (c) 2017, CodeMettle
 */
package jsactor.bridge

/**
  * Created by steven on 10/12/2017.
  */
package object protocol {
  implicit class RichCirceEither[T](val e: Either[io.circe.Error, T]) {
    def valueOrThrow: T = e match {
      case Left(t) ⇒ throw t
      case Right(v) ⇒ v
    }
  }
}
