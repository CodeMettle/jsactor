/*
 * BooPickleClientBridgeActor.scala
 *
 * Updated: Mar 3, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.client

import jsactor.JsProps
import jsactor.bridge.client.WebSocketActor.WebSocketSendable
import jsactor.bridge.protocol.{BooPickleBridgeProtocol, BooPickleProtocolPickler}
import scala.reflect.ClassTag
import scala.scalajs.js

/**
  * @author steven
  *
  */
object BooPickleClientBridgeActor {
  def props(implicit bridgeProtocol: BooPickleBridgeProtocol) =
    JsProps(new BooPickleClientBridgeActor)
}

class BooPickleClientBridgeActor(implicit val bridgeProtocol: BooPickleBridgeProtocol)
  extends ClientBridgeActor[Array[Byte], js.typedarray.ArrayBuffer] {
  import scala.language.implicitConversions

  override protected implicit def pickleWSS: WebSocketSendable[Array[Byte]] = WebSocketSendable.ByteArrWSS

  override protected implicit def recvCT: ClassTag[js.typedarray.ArrayBuffer] = ClassTag(classOf[js.typedarray.ArrayBuffer])

  override protected implicit def recvToPickleFmt(r: js.typedarray.ArrayBuffer): Array[Byte] = {
    import scala.scalajs.js.typedarray._

    val arr = new Int8Array(r)
    arr.toArray
  }

  override protected def newProtocolPickler = new BooPickleProtocolPickler

}
