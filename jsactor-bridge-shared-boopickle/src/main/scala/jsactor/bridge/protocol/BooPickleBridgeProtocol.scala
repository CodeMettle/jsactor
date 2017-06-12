/*
 * BooPickleBridgeProtocol.scala
 *
 * Updated: Mar 1, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.protocol

import java.nio.ByteBuffer

import boopickle.DefaultBasic._
import boopickle.{PickleState, UnpickleState}

import jsactor.bridge.protocol.BooPickleBridgeProtocol.{MessageRegistry, failureEntry, successEntry}
import scala.annotation.implicitNotFound
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * @author steven
  *
  */
object BooPickleBridgeProtocol {
  class MessageRegistry {
    private[BooPickleBridgeProtocol] var msgMap = Map.empty[String, Pickler[_]]

    def add[A : Pickler : ClassTag] =
      msgMap += (implicitly[ClassTag[A]].runtimeClass.getName → implicitly[Pickler[A]])

    def addObj[A <: Singleton : Pickler : ClassTag](obj: A) =
      msgMap += (implicitly[ClassTag[A]].runtimeClass.getName → implicitly[Pickler[A]])
  }

  private val failureEntry = 0.toByte
  private val successEntry = 1.toByte
}

@implicitNotFound("Need an implicit BooPickleBridgeProtocol in scope, consider creating an implicit object extending BooPickleBridgeProtocol")
trait BooPickleBridgeProtocol extends BridgeProtocol[Array[Byte]] {
  private val registry = new MessageRegistry
  registerMessages(registry)
  private val msgMap = registry.msgMap

  /**
    * Register messages that go across bridge
    *
    * @param registry messages are registered with registry.add / registry.addObj
    */
  def registerMessages(registry: MessageRegistry): Unit

  def pickleBB(obj: Any): ByteBuffer = obj match {
    case StatusFailure(cause) ⇒ Pickle.intoBytes(failureEntry → pickleBB(cause))
    case _ ⇒
      val className = obj.getClass.getName

      implicit val pickler = msgMap.getOrElse(className, sys.error(s"$className is not registered")).asInstanceOf[Pickler[Any]]
      val pickled = {
        val state = PickleState.pickleStateSpeed
        pickler.pickle(obj)(state)
        state.toByteBuffer
      }

      Pickle.intoBytes(successEntry → Pickle.intoBytes(className → pickled))
  }

  def pickle(obj: Any): Array[Byte] = bb2arr(pickleBB(obj))

  def unpickleBB(bb: ByteBuffer): Try[Any] = {
    Try(Unpickle[(Byte, ByteBuffer)].fromBytes(bb)) flatMap {
      case (entType, pickled) ⇒
        if (entType == failureEntry)
          unpickleBB(pickled) flatMap {
            case t: Throwable ⇒ Success(StatusFailure(t))
            case _ ⇒ Failure(new Exception("Expected throwable"))
          }
        else if (entType == successEntry) Try {
          val (className, objPickled) = Unpickle[(String, ByteBuffer)].fromBytes(pickled)

          val pickler = msgMap.getOrElse(className, sys.error(s"$className is not registered"))

          pickler.unpickle(UnpickleState(objPickled))
        } else
          Failure(new Exception("Expected either failure or success"))
    }
  }

  def unpickle(arr: Array[Byte]): Any = unpickleBB(ByteBuffer wrap arr).get
}
