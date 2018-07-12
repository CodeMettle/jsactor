/*
 * ScalaPBBridgeProtocol.scala
 *
 * Updated: Jul 12, 2018
 *
 * Copyright (c) 2018, CodeMettle
 */
package jsactor.bridge.protocol

import com.google.protobuf.ByteString
import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}

import jsactor.bridge.protocol.ScalaPBBridgeProtocol.{MessageRegistry, Pickler}
import scala.annotation.implicitNotFound
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Created by steven on 7/12/2018.
  */
object ScalaPBBridgeProtocol {
  trait Pickler[T <: GeneratedMessage] {
    def pickle(t: T): Array[Byte] = t.toByteArray
    def unpickle(bytes: Array[Byte]): T
  }

  object Pickler {
    def apply[T <: GeneratedMessage with Message[T]](implicit cmp: GeneratedMessageCompanion[T]): Pickler[T] = new Pickler[T] {
      override def unpickle(bytes: Array[Byte]): T = cmp.parseFrom(bytes)
    }
  }

  class MessageRegistry {
    private[ScalaPBBridgeProtocol] var msgMap = Map.empty[String, Pickler[_]]

    def add[T <: GeneratedMessage with Message[T]](implicit cmp: GeneratedMessageCompanion[T], ct: ClassTag[T]): Unit =
      msgMap += (ct.runtimeClass.getName → Pickler[T])
  }
}

@implicitNotFound("Need an implicit ScalaPBBridgeProtocol in scope, consider creating an implicit object extending ScalaPBBridgeProtocol")
trait ScalaPBBridgeProtocol extends BridgeProtocol[Array[Byte]] {
  private val registry = new MessageRegistry
  registerMessages(registry)
  private val msgMap = registry.msgMap

  /**
    * Register messages that go across bridge
    *
    * @param registry messages are registered with registry.add / registry.addObj
    */
  def registerMessages(registry: MessageRegistry): Unit

  def pickle(obj: Any): Array[Byte] = obj match {
    case StatusFailure(cause) ⇒
      MessageWrapper.MessageWrapperTypeMapper.toBase(FailureMessage(ByteString.copyFrom(pickle(cause)))).toByteArray

    case obj: GeneratedMessage ⇒
      val className = obj.getClass.getName

      val pickler = msgMap.getOrElse(className, sys.error(s"$className is not registered")).asInstanceOf[Pickler[GeneratedMessage]]
      val pickled = pickler.pickle(obj)

      MessageWrapper.MessageWrapperTypeMapper.toBase(NormalMessage(className, ByteString.copyFrom(pickled))).toByteArray
  }

  def tryUnpickle(bb: Array[Byte]): Try[Any] = {
    Try(MessageWrapperMessage.parseFrom(bb)).map(MessageWrapper.MessageWrapperTypeMapper.toCustom) flatMap {
      case FailureMessage(bytes) ⇒
        tryUnpickle(bytes.toByteArray) flatMap {
          case t: Throwable ⇒ Success(StatusFailure(t))
          case _ ⇒ Failure(new Exception("Expected throwable"))
        }

      case NormalMessage(className, bytes) ⇒ Try {
        val pickler = msgMap.getOrElse(className, sys.error(s"$className is not registered"))

        pickler.unpickle(bytes.toByteArray)
      }

      case _ ⇒ Failure(new Exception("Expected either FailureMessage or NormalMessage"))
    }
  }

  def unpickle(arr: Array[Byte]): Any = tryUnpickle(arr).get
}
