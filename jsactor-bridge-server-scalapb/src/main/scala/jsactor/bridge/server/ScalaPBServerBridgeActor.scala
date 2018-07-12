/*
 * ScalaPBServerBridgeActor.scala
 *
 * Updated: Jul 12, 2018
 *
 * Copyright (c) 2018, CodeMettle
 */
package jsactor.bridge.server

import akka.actor.{ActorRef, Props}
import jsactor.bridge.protocol.{ProtocolPickler, ScalaPBBridgeProtocol, ScalaPBProtocolPickler}
import jsactor.bridge.server.ServerBridgeActor.WebSocketSendable
import scala.reflect.ClassTag

/**
  * Created by steven on 7/12/2018.
  */
object ScalaPBServerBridgeActor {
  def props(clientWebSocket: ActorRef)(implicit bridgeProtocol: ScalaPBBridgeProtocol) =
    Props(new ScalaPBServerBridgeActor(clientWebSocket))
}

class ScalaPBServerBridgeActor(val clientWebSocket: ActorRef)
                                (implicit val bridgeProtocol: ScalaPBBridgeProtocol)
  extends ServerBridgeActor[Array[Byte]] {

  override protected implicit def pickleWSS: WebSocketSendable[Array[Byte]] = WebSocketSendable.ArrWSS

  override protected implicit def pickleToCT: ClassTag[Array[Byte]] = ClassTag(classOf[Array[Byte]])

  override protected def newProtocolPickler: ProtocolPickler[Array[Byte]] = new ScalaPBProtocolPickler

}
