/*
 * BooPickleSocketManager.scala
 *
 * Updated: Mar 3, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.client

import jsactor.bridge.client.BooPickleSocketManager.Config
import jsactor.bridge.protocol.BooPickleBridgeProtocol
import jsactor.{JsActorRefFactory, JsProps}
import scala.concurrent.duration._

/**
  * @author steven
  *
  */
object BooPickleSocketManager {
  def props(config: Config)(implicit bridgeProtocol: BooPickleBridgeProtocol) =
    JsProps(new BooPickleSocketManager(config))

  case class Config(wsUrl: String,
                    clientBridgeActorProps: (BooPickleBridgeProtocol) ⇒ JsProps = BooPickleClientBridgeActor.props(_),
                    webSocketActorProps: (String, JsProps) ⇒ JsProps = WebSocketActor.props,
                    initialReconnectTime: FiniteDuration = 125.millis, maxReconnectTime: FiniteDuration = 4.seconds)
    extends SocketManager.Config[BooPickleBridgeProtocol, Array[Byte]]
}

class BooPickleSocketManager(val config: Config)
                            (implicit val bridgeProtocol: BooPickleBridgeProtocol)
  extends SocketManager[BooPickleBridgeProtocol, Array[Byte]]

class BooPickleWebSocketManager(config: Config, name: String = "socketManager")
                               (implicit arf: JsActorRefFactory, bridgeProtocol: BooPickleBridgeProtocol)
  extends WebSocketManager {
  val socketManager = arf.actorOf(BooPickleSocketManager.props(config), name)
}
