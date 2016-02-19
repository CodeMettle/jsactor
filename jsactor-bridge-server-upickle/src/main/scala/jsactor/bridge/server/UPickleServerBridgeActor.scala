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
import jsactor.bridge.server.ServerBridgeActor.WebSocketSendable
import scala.reflect.ClassTag

object UPickleServerBridgeActor {
  def props(clientWebSocket: ActorRef)(implicit bridgeProtocol: UPickleBridgeProtocol) = {
    Props(new UPickleServerBridgeActor(clientWebSocket))
  }
}

class UPickleServerBridgeActor(val clientWebSocket: ActorRef)
                              (implicit val bridgeProtocol: UPickleBridgeProtocol)
  extends ServerBridgeActor[Js.Value, String] {

  override protected implicit def pickleWSS: WebSocketSendable[String] = WebSocketSendable.StrWSS

  override protected implicit def pickleToCT = ClassTag(classOf[String])

  override protected def newProtocolPickler = new UPickleProtocolPickler

}
