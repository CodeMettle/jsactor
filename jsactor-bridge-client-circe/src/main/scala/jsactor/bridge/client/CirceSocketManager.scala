/*
 * CirceSocketManager.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.client

import akka.actor.{ActorRef, ActorRefFactory, Props}
import jsactor.bridge.client.CirceSocketManager.Config
import jsactor.bridge.protocol.CirceBridgeProtocol
import scala.concurrent.duration._

/**
  * @author steven
  *
  */
object CirceSocketManager {
  def props(config: CirceSocketManager.Config)(implicit bridgeProtocol: CirceBridgeProtocol) =
    Props(new CirceSocketManager(config))

  case class Config(wsUrl: String,
                    clientBridgeActorProps: (CirceBridgeProtocol) ⇒ Props = CirceClientBridgeActor.props(_),
                    webSocketActorProps: (String, Props) ⇒ Props = WebSocketActor.props,
                    initialReconnectTime: FiniteDuration = 125.millis,
                    maxReconnectTime: FiniteDuration = 4.seconds) extends SocketManager.Config[CirceBridgeProtocol, String]

}

class CirceSocketManager(val config: Config)
                        (implicit val bridgeProtocol: CirceBridgeProtocol) extends SocketManager[CirceBridgeProtocol, String]

class CirceWebSocketManager(config: CirceSocketManager.Config, name: String = "socketManager")
                           (implicit arf: ActorRefFactory,
                            bridgeProtocol: CirceBridgeProtocol) extends WebSocketManager {
  val socketManager: ActorRef = arf.actorOf(CirceSocketManager.props(config), name)
}
