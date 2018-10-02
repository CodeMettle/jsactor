/*
 * ScalaPBSocketManager.scala
 *
 * Updated: Jul 12, 2018
 *
 * Copyright (c) 2018, CodeMettle
 */
package jsactor.bridge.client

import akka.actor.{ActorRef, ActorRefFactory, Props}
import jsactor.bridge.client.ScalaPBSocketManager.Config
import jsactor.bridge.protocol.ScalaPBBridgeProtocol
import scala.concurrent.duration._

/**
  * Created by steven on 7/12/2018.
  */
object ScalaPBSocketManager {
  def props(config: Config)(implicit bridgeProtocol: ScalaPBBridgeProtocol) =
    Props(new ScalaPBSocketManager(config))

  case class Config(wsUrl: String,
                    clientBridgeActorProps: (ScalaPBBridgeProtocol) ⇒ Props = ScalaPBClientBridgeActor.props(_),
                    webSocketActorProps: (String, Props) ⇒ Props = WebSocketActor.props,
                    initialReconnectTime: FiniteDuration = 125.millis, maxReconnectTime: FiniteDuration = 4.seconds)
    extends SocketManager.Config[ScalaPBBridgeProtocol, Array[Byte]]
}

class ScalaPBSocketManager(val config: Config)
                            (implicit val bridgeProtocol: ScalaPBBridgeProtocol)
  extends SocketManager[ScalaPBBridgeProtocol, Array[Byte]]

class ScalaPBWebSocketManager(config: Config, name: String = "socketManager")
                               (implicit arf: ActorRefFactory, bridgeProtocol: ScalaPBBridgeProtocol)
  extends WebSocketManager {
  val socketManager: ActorRef = arf.actorOf(ScalaPBSocketManager.props(config), name)
}
