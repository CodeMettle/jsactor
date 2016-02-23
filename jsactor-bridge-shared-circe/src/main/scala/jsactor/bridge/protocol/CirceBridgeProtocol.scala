/*
 * CirceBridgeProtocol.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.protocol

import cats.data.Xor
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import jsactor.bridge.protocol.CirceBridgeProtocol.{MessageRegistry, failureEntry, successEntry}
import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

/**
  * @author steven
  *
  */
object CirceBridgeProtocol {
  class MessageRegistry {
    private[CirceBridgeProtocol] var msgMap = Map.empty[String, (Decoder[_], Encoder[_])]

    def add[A : Decoder : Encoder : ClassTag] = {
      msgMap += (implicitly[ClassTag[A]].runtimeClass.getName → (implicitly[Decoder[A]] → implicitly[Encoder[A]]))
    }

    def addObj[A <: Singleton : Decoder : Encoder : ClassTag](obj: A) = {
      msgMap += (implicitly[ClassTag[A]].runtimeClass.getName → (implicitly[Decoder[A]] → implicitly[Encoder[A]]))
    }
  }

  private val failureEntry = "__failure__"
  private val successEntry = "__success__"
}

@implicitNotFound("Need an implicit CirceBridgeProtocol in scope, consider creating an implicit object extending CirceBridgeProtocol")
trait CirceBridgeProtocol extends BridgeProtocol[String] {
  private val registry = new MessageRegistry
  registerMessages(registry)
  private val msgMap = registry.msgMap

  /**
    * Register messages that go across bridge
    *
    * @param registry messages are registered with registry.add / registry.addObj
    */
  def registerMessages(registry: MessageRegistry): Unit

  def pickleJs(obj: Any): Json = {
    val encoder = Encoder[(String, Json)]

    obj match {
      case StatusFailure(cause) ⇒ encoder(failureEntry → pickleJs(cause))

      case _ ⇒
        val className = obj.getClass.getName

        val (_, objEncoder) = msgMap.getOrElse(className, sys.error(s"$className is not registered"))

        encoder(successEntry → encoder(className → objEncoder.asInstanceOf[Encoder[Any]](obj)))
    }
  }

  def pickle(obj: Any): String = pickleJs(obj).noSpaces

  private def unpickleCursor(c: ACursor): Decoder.Result[Any] = {
    def error(err: String) = DecodingFailure(err, Nil)

    c.as[(String, Json)] flatMap {
      case (`failureEntry`, jsVal) ⇒ unpickleCursor(ACursor ok jsVal.hcursor) flatMap {
        case t: Throwable ⇒ Xor right StatusFailure(t)
        case other ⇒ Xor left error(s"Expected Throwable for failure, got $other")
      }

      case (`successEntry`, jsVal) ⇒ jsVal.as[(String, Json)] flatMap {
        case (className, js) ⇒ msgMap get className match {
          case None ⇒ Xor left error(s"$className is not registered")
          case Some((decoder, _)) ⇒ decoder.apply(js.hcursor)
        }
      }

      case (other, _) ⇒ Xor left error(s"Expected failure or success, got $other")
    }
  }

  def unpickleJs(js: Json): Decoder.Result[Any] = unpickleCursor(ACursor ok js.hcursor)

  def unpickle(json: String): Any = parse(json).flatMap(unpickleJs).valueOr(throw _)
}
