/*
 * UPickleClientBridgeActor.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.client

import upickle._

import jsactor.JsProps
import jsactor.bridge.protocol.{UPickleBridgeProtocol, UPickleProtocolPickler}

/**
  * @author steven
  *
  */
object UPickleClientBridgeActor {
    def props(implicit bridgeProtocol: UPickleBridgeProtocol) = {
      JsProps(new UPickleClientBridgeActor)
    }
}

class UPickleClientBridgeActor(implicit val bridgeProtocol: UPickleBridgeProtocol) extends ClientBridgeActor[Js.Value] {

  override protected def newProtocolPickler = new UPickleProtocolPickler

}
