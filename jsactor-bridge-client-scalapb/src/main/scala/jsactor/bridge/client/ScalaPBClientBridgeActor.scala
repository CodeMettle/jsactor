/*
 * ScalaPBClientBridgeActor.scala
 *
 * Updated: Jul 12, 2018
 *
 * Copyright (c) 2018, CodeMettle
 */
package jsactor.bridge.client

import akka.actor.Props
import jsactor.bridge.client.WebSocketActor.WebSocketSendable
import jsactor.bridge.protocol.{ScalaPBBridgeProtocol, ScalaPBProtocolPickler}
import scala.reflect.ClassTag
import scala.scalajs.js

/**
  * Created by steven on 7/12/2018.
  */
object ScalaPBClientBridgeActor {
  def props(implicit bridgeProtocol: ScalaPBBridgeProtocol) =
    Props(new ScalaPBClientBridgeActor)
}

class ScalaPBClientBridgeActor(implicit val bridgeProtocol: ScalaPBBridgeProtocol)
  extends ClientBridgeActor[Array[Byte], js.typedarray.ArrayBuffer] {
  import scala.language.implicitConversions

  override protected implicit def pickleWSS: WebSocketSendable[Array[Byte]] = WebSocketSendable.ByteArrWSS

  override protected implicit def recvCT: ClassTag[js.typedarray.ArrayBuffer] = ClassTag(classOf[js.typedarray.ArrayBuffer])

  override protected implicit def recvToPickleFmt(r: js.typedarray.ArrayBuffer): Array[Byte] = {
    import scala.scalajs.js.typedarray._

    val arr = new Int8Array(r)
    arr.toArray
  }

  override protected def newProtocolPickler = new ScalaPBProtocolPickler

}
