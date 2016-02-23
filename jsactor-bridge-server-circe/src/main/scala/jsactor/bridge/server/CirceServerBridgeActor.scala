/*
 * CirceServerBridgeActor.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.server

import akka.actor.{ActorRef, Props}
import jsactor.bridge.protocol.{CirceBridgeProtocol, CirceProtocolPickler}
import jsactor.bridge.server.ServerBridgeActor.WebSocketSendable
import scala.reflect.ClassTag

/**
  * @author steven
  *
  */
object CirceServerBridgeActor {
  def props(clientWebSocket: ActorRef)(implicit bridgeProtocol: CirceBridgeProtocol) =
    Props(new CirceServerBridgeActor(clientWebSocket))
}

class CirceServerBridgeActor(val clientWebSocket: ActorRef)
                            (implicit val bridgeProtocol: CirceBridgeProtocol) extends ServerBridgeActor[String] {

  override protected implicit def pickleWSS: WebSocketSendable[String] = WebSocketSendable.StrWSS

  override protected implicit def pickleToCT: ClassTag[String] = ClassTag(classOf[String])

  override protected def newProtocolPickler = new CirceProtocolPickler

}
