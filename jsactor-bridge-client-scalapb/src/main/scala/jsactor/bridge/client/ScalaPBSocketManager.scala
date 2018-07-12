/*
 * ScalaPBSocketManager.scala
 *
 * Updated: Jul 12, 2018
 *
 * Copyright (c) 2018, CodeMettle
 */
package jsactor.bridge.client

import jsactor.{JsActorRef, JsActorRefFactory, JsProps}
import jsactor.bridge.client.ScalaPBSocketManager.Config
import jsactor.bridge.protocol.ScalaPBBridgeProtocol
import scala.concurrent.duration._

/**
  * Created by steven on 7/12/2018.
  */
object ScalaPBSocketManager {
  def props(config: Config)(implicit bridgeProtocol: ScalaPBBridgeProtocol) =
    JsProps(new ScalaPBSocketManager(config))

  case class Config(wsUrl: String,
                    clientBridgeActorProps: (ScalaPBBridgeProtocol) ⇒ JsProps = ScalaPBClientBridgeActor.props(_),
                    webSocketActorProps: (String, JsProps) ⇒ JsProps = WebSocketActor.props,
                    initialReconnectTime: FiniteDuration = 125.millis, maxReconnectTime: FiniteDuration = 4.seconds)
    extends SocketManager.Config[ScalaPBBridgeProtocol, Array[Byte]]
}

class ScalaPBSocketManager(val config: Config)
                            (implicit val bridgeProtocol: ScalaPBBridgeProtocol)
  extends SocketManager[ScalaPBBridgeProtocol, Array[Byte]]

class ScalaPBWebSocketManager(config: Config, name: String = "socketManager")
                               (implicit arf: JsActorRefFactory, bridgeProtocol: ScalaPBBridgeProtocol)
  extends WebSocketManager {
  val socketManager: JsActorRef = arf.actorOf(ScalaPBSocketManager.props(config), name)
}
