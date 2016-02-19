/*
 * UPickleSocketManager.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.client

import upickle._

import jsactor.bridge.client.UPickleSocketManager.Config
import jsactor.bridge.protocol.{BridgeProtocol, UPickleBridgeProtocol}
import jsactor.{JsActorRefFactory, JsProps}
import scala.concurrent.duration._

/**
  * @author steven
  *
  */
object UPickleSocketManager {
    def props(config: UPickleSocketManager.Config)(implicit bridgeProtocol: BridgeProtocol) = {
      JsProps(new UPickleSocketManager(config))
    }

  case class Config(wsUrl: String,
                    clientBridgeActorProps: (UPickleBridgeProtocol) ⇒ JsProps = UPickleClientBridgeActor.props(_),
                    webSocketActorProps: (String, JsProps) ⇒ JsProps = WebSocketActor.props,
                    initialReconnectTime: FiniteDuration = 125.millis,
                    maxReconnectTime: FiniteDuration = 4.seconds) extends SocketManager.Config[Js.Value]

}

class UPickleSocketManager(val config: Config)
                          (implicit val bridgeProtocol: UPickleBridgeProtocol) extends SocketManager[Js.Value]

class UPickleWebSocketManager(config: UPickleSocketManager.Config, name: String = "socketManager")
                             (implicit arf: JsActorRefFactory, bridgeProtocol: BridgeProtocol) extends WebSocketManager {
  val socketManager = arf.actorOf(UPickleSocketManager.props(config), name)
}
