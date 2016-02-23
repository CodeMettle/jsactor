/*
 * UPickleBridgeProtocol.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.protocol

import upickle._
import upickle.default._

import jsactor.bridge.protocol.UPickleBridgeProtocol.{MessageRegistry, failureEntry}
import scala.annotation.implicitNotFound
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
 * @author steven
 *
 */
object UPickleBridgeProtocol {
  class MessageRegistry {
    private[UPickleBridgeProtocol] var msgMap = Map.empty[String, (Reader[_], Writer[_])]

    def add[A : Reader : Writer : ClassTag] = {
      msgMap += (implicitly[ClassTag[A]].runtimeClass.getName → (implicitly[Reader[A]] → implicitly[Writer[A]]))
    }

    def addObj[A <: Singleton : Reader : Writer : ClassTag](obj: A) = {
      msgMap += (implicitly[ClassTag[A]].runtimeClass.getName → (implicitly[Reader[A]] → implicitly[Writer[A]]))
    }
  }

  private val failureEntry = "__failure__"
}

@implicitNotFound("Need an implicit UPickleBridgeProtocol in scope, consider creating an implicit object extending UPickleBridgeProtocol")
trait UPickleBridgeProtocol extends BridgeProtocol[String] {
  private val registry = new MessageRegistry
  registerMessages(registry)
  private val msgMap = registry.msgMap

  /**
   * Register messages that go across bridge
   *
   * @param registry messages are registered with registry.add / registry.addObj
   */
  def registerMessages(registry: MessageRegistry): Unit

  def pickleJs(obj: Any): Js.Value = {
    obj match {
      case StatusFailure(cause) ⇒ Js.Obj(failureEntry → pickleJs(cause))

      case _ ⇒
        val className = obj.getClass.getName

        val (_, writer) = msgMap.getOrElse(className, sys.error(s"$obj is not registered"))

        Js.Arr(Js.Str(className), writer.asInstanceOf[Writer[Any]].write(obj))
    }
  }

  def pickle(obj: Any): String = {
    json.write(pickleJs(obj))
  }

  def unpickleJs(js: Js.Value): Try[Any] = js match {
    case obj: Js.Obj ⇒
      obj.value find (_._1 == failureEntry) match {
        case None ⇒ Failure(Invalid.Data(obj, "Expected a failure entry"))

        case Some((_, arr: Js.Arr)) ⇒ unpickleJs(arr) flatMap {
          case t: Throwable ⇒ Success(StatusFailure(t))
          case _ ⇒ Failure(Invalid.Data(obj, "Expected a failure cause"))
        }

        case _ ⇒ Failure(Invalid.Data(obj, "Expected an array with failure cause"))
      }

    case arr: Js.Arr ⇒
      if (arr.value.size != 2)
        Failure(Invalid.Data(arr, "Expected 2 elements"))
      else {
        val className = readJs[String](arr.value(0))
        val jsVal = arr.value(1)

        val (reader, _) = msgMap.getOrElse(className, throw Invalid.Data(arr, s"$className is not registered"))

        Try(reader.read(jsVal))
      }

    case jsval ⇒ Failure(Invalid.Data(jsval, "Expected an Array of 2 elements or a failure"))
  }

  def unpickle(json: String): Any = unpickleJs(upickle.json.read(json)).get
}
