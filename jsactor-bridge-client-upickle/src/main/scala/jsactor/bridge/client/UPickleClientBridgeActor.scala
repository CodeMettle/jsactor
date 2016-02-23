/*
 * UPickleClientBridgeActor.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.client

import jsactor.JsProps
import jsactor.bridge.client.WebSocketActor.WebSocketSendable
import jsactor.bridge.protocol.{UPickleBridgeProtocol, UPickleProtocolPickler}
import scala.reflect.ClassTag

/**
  * @author steven
  *
  */
object UPickleClientBridgeActor {
    def props(implicit bridgeProtocol: UPickleBridgeProtocol) = {
      JsProps(new UPickleClientBridgeActor)
    }
}

class UPickleClientBridgeActor(implicit val bridgeProtocol: UPickleBridgeProtocol)
  extends ClientBridgeActor[String] {

  override protected implicit def pickleWSS: WebSocketSendable[String] = WebSocketSendable.StrWSS

  override protected implicit def pickleCT: ClassTag[String] = ClassTag(classOf[String])

  override protected def newProtocolPickler = new UPickleProtocolPickler

}
