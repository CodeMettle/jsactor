/*
 * CirceClientBridgeActor.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.client

import jsactor.JsProps
import jsactor.bridge.client.WebSocketActor.WebSocketSendable
import jsactor.bridge.protocol.{CirceBridgeProtocol, CirceProtocolPickler}
import scala.reflect.ClassTag

/**
  * @author steven
  *
  */
object CirceClientBridgeActor {
  def props(implicit bridgeProtocol: CirceBridgeProtocol) =
    JsProps(new CirceClientBridgeActor)
}

class CirceClientBridgeActor(implicit val bridgeProtocol: CirceBridgeProtocol)
  extends ClientBridgeActor[String, String] {
  import scala.language.implicitConversions

  override protected implicit def pickleWSS: WebSocketSendable[String] = WebSocketSendable.StrWSS

  override protected implicit def recvCT: ClassTag[String] = ClassTag(classOf[String])

  override protected implicit def recvToPickleFmt(r: String): String = r

  override protected def newProtocolPickler = new CirceProtocolPickler

}
