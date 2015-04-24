/*
 * BridgeProtocol.scala
 *
 * Updated: Apr 23, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.protocol

import upickle._

import jsactor.bridge.protocol.BridgeProtocol.{failureEntry, MessageRegistry}
import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

/**
 * @author steven
 *
 */
object BridgeProtocol {
  class MessageRegistry {
    private[BridgeProtocol] var msgMap = Map.empty[String, (Reader[_], Writer[_])]

    def add[A : Reader : Writer : ClassTag] = {
      msgMap += (implicitly[ClassTag[A]].runtimeClass.getName → (implicitly[Reader[A]] → implicitly[Writer[A]]))
    }

    def addObj[A <: Singleton : Reader : Writer : ClassTag](obj: A) = {
      msgMap += (implicitly[ClassTag[A]].runtimeClass.getName → (implicitly[Reader[A]] → implicitly[Writer[A]]))
    }
  }

  private val failureEntry = "__failure__"
}

@implicitNotFound("Need an implicit BridgeProtocol in scope, consider creating an implicit object extending BridgeProtocol")
trait BridgeProtocol {
  private val registry = new MessageRegistry
  registerMessages(registry)
  private val msgMap = registry.msgMap

  /**
   * Register messages that go across bridge
   *
   * @param registry messages are registered with registry.add / registry.addObj
   */
  def registerMessages(registry: MessageRegistry): Unit

  private[bridge] def pickleJs(obj: Any): Js.Value = {
    obj match {
      case StatusFailure(cause) ⇒ Js.Obj(failureEntry → pickleJs(obj))

      case _ ⇒
        val className = obj.getClass.getName

        val (_, writer) = msgMap.getOrElse(className, sys.error(s"$obj is not registered"))

        Js.Arr(Js.Str(className), writer.asInstanceOf[Writer[Any]].write(obj))
    }
  }

  private[bridge] def pickle(obj: Any): String = {
    json.write(pickleJs(obj))
  }

  private[bridge] def unpickleJs(js: Js.Value): Any = js match {
    case obj: Js.Obj ⇒
      obj.value find (_._1 == failureEntry) match {
        case None ⇒ throw Invalid.Data(obj, "Expected a failure entry")

        case Some((_, arr: Js.Arr)) ⇒ unpickleJs(arr) match {
          case t: Throwable ⇒ StatusFailure(t)
          case _ ⇒ throw Invalid.Data(obj, "Expected a failure cause")
        }

        case _ ⇒ throw Invalid.Data(obj, "Expected an array with failure cause")
      }

    case arr: Js.Arr ⇒
      if (arr.value.size != 2)
        throw Invalid.Data(arr, "Expected 2 elements")

      val className = readJs[String](arr.value(0))
      val jsVal = arr.value(1)

      val (reader, _) = msgMap.getOrElse(className, throw Invalid.Data(arr, s"$className is not registered"))

      reader.read(jsVal)

    case jsval ⇒ throw Invalid.Data(jsval, "Expected an Array of 2 elements or a failure")
  }

  private[bridge] def unpickle(json: String): Any = {
    unpickleJs(upickle.json.read(json))
  }
}