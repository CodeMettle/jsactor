/*
 * CirceSocketManager.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.client

import jsactor.bridge.client.CirceSocketManager.Config
import jsactor.bridge.protocol.CirceBridgeProtocol
import jsactor.{JsActorRefFactory, JsProps}
import scala.concurrent.duration._

/**
  * @author steven
  *
  */
object CirceSocketManager {
  def props(config: CirceSocketManager.Config)(implicit bridgeProtocol: CirceBridgeProtocol) =
    JsProps(new CirceSocketManager(config))

  case class Config(wsUrl: String,
                    clientBridgeActorProps: (CirceBridgeProtocol) ⇒ JsProps = CirceClientBridgeActor.props(_),
                    webSocketActorProps: (String, JsProps) ⇒ JsProps = WebSocketActor.props,
                    initialReconnectTime: FiniteDuration = 125.millis,
                    maxReconnectTime: FiniteDuration = 4.seconds) extends SocketManager.Config[CirceBridgeProtocol, String]

}

class CirceSocketManager(val config: Config)
                        (implicit val bridgeProtocol: CirceBridgeProtocol) extends SocketManager[CirceBridgeProtocol, String]

class CirceWebSocketManager(config: CirceSocketManager.Config, name: String = "socketManager")
                           (implicit arf: JsActorRefFactory,
                            bridgeProtocol: CirceBridgeProtocol) extends WebSocketManager {
  val socketManager = arf.actorOf(CirceSocketManager.props(config), name)
}
