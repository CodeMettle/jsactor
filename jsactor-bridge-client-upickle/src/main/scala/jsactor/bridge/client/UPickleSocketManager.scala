/*
 * UPickleSocketManager.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.client

import akka.actor.{ActorRef, ActorRefFactory, Props}
import jsactor.bridge.client.UPickleSocketManager.Config
import jsactor.bridge.protocol.UPickleBridgeProtocol
import scala.concurrent.duration._

/**
  * @author steven
  *
  */
object UPickleSocketManager {
    def props(config: UPickleSocketManager.Config)(implicit bridgeProtocol: UPickleBridgeProtocol) =
      Props(new UPickleSocketManager(config))

  case class Config(wsUrl: String,
                    clientBridgeActorProps: (UPickleBridgeProtocol) ⇒ Props = UPickleClientBridgeActor.props(_),
                    webSocketActorProps: (String, Props) ⇒ Props = WebSocketActor.props,
                    initialReconnectTime: FiniteDuration = 125.millis,
                    maxReconnectTime: FiniteDuration = 4.seconds) extends SocketManager.Config[UPickleBridgeProtocol, String]

}

class UPickleSocketManager(val config: Config)
                          (implicit val bridgeProtocol: UPickleBridgeProtocol) extends SocketManager[UPickleBridgeProtocol, String]

class UPickleWebSocketManager(config: UPickleSocketManager.Config, name: String = "socketManager")
                             (implicit arf: ActorRefFactory, bridgeProtocol: UPickleBridgeProtocol)
  extends WebSocketManager {
  val socketManager: ActorRef = arf.actorOf(UPickleSocketManager.props(config), name)
}
