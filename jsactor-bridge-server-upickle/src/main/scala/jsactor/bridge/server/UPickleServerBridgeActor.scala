/*
 * UPickleServerBridgeActor.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.server

import upickle._

import akka.actor.{ActorRef, Props}
import jsactor.bridge.protocol.{UPickleBridgeProtocol, UPickleProtocolPickler}

object UPickleServerBridgeActor {
  def props(clientWebSocket: ActorRef)(implicit bridgeProtocol: UPickleBridgeProtocol) = {
    Props(new UPickleServerBridgeActor(clientWebSocket))
  }
}

class UPickleServerBridgeActor(val clientWebSocket: ActorRef)
                              (implicit val bridgeProtocol: UPickleBridgeProtocol) extends ServerBridgeActor[Js.Value] {

  override protected def newProtocolPickler = new UPickleProtocolPickler

}
