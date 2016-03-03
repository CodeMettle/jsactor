/*
 * BooPickleServerBridgeActor.scala
 *
 * Updated: Mar 3, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.server

import akka.actor.{ActorRef, Props}
import jsactor.bridge.protocol.{BooPickleBridgeProtocol, BooPickleProtocolPickler, ProtocolPickler}
import jsactor.bridge.server.ServerBridgeActor.WebSocketSendable
import scala.reflect.ClassTag

/**
  * @author steven
  *
  */
object BooPickleServerBridgeActor {
  def props(clientWebSocket: ActorRef)(implicit bridgeProtocol: BooPickleBridgeProtocol) =
    Props(new BooPickleServerBridgeActor(clientWebSocket))
}

class BooPickleServerBridgeActor(val clientWebSocket: ActorRef)
                                (implicit val bridgeProtocol: BooPickleBridgeProtocol)
  extends ServerBridgeActor[Array[Byte]] {

  override protected implicit def pickleWSS: WebSocketSendable[Array[Byte]] = WebSocketSendable.ArrWSS

  override protected implicit def pickleToCT: ClassTag[Array[Byte]] = ClassTag(classOf[Array[Byte]])

  override protected def newProtocolPickler: ProtocolPickler[Array[Byte]] = new BooPickleProtocolPickler

}
