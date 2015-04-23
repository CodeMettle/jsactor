/*
 * SocketManager.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.client

import com.codemettle.weblogging.WebLogging

import jsactor.RippedFromAkka.base64
import jsactor._
import jsactor.bridge.client.SocketManager.Events._
import jsactor.bridge.client.SocketManager.InternalMessages.Connected
import jsactor.bridge.client.SocketManager._
import jsactor.bridge.protocol.BridgeProtocol
import scala.concurrent.duration._

/**
 * @author steven
 *
 */
object SocketManager {
  def props(config: SocketManager.Config)(implicit bridgeProtocol: BridgeProtocol) = {
    JsProps(new SocketManager(config))
  }

  case class Config(wsUrl: String, clientBridgeActorProps: (BridgeProtocol) ⇒ JsProps = ClientBridgeActor.props(_),
                    webSocketActorProps: (String, JsProps) ⇒ JsProps = WebSocketActor.props,
                    initialReconnectTime: FiniteDuration = 125.millis, maxReconnectTime: FiniteDuration = 4.seconds)

  private object fsm {
    case class Data(reconnectTime: FiniteDuration, wsActor: Option[JsActorRef])
  }

  object InternalMessages {
    case object Connected
  }

  object Events {
    case object SubscribeToEvents

    sealed trait SocketManagerEvent
    case class WebSocketConnected(socket: JsActorRef) extends SocketManagerEvent
    case object WebSocketDisconnected extends SocketManagerEvent
    case object WebSocketShutdown extends SocketManagerEvent
  }

  private case object TryToConnect
}

class SocketManager(config: Config)(implicit bridgeProtocol: BridgeProtocol) extends JsActor with WebLogging {
  private val wsActorName = Iterator from 0 map (i ⇒ s"ws${base64(i)}")

  private var reconnectTimer = Option.empty[JsCancellable]

  private var subscribers = Set.empty[JsActorRef]

  override def preStart() = {
    super.preStart()

    self ! TryToConnect

    context become disconnected(fsm.Data(config.initialReconnectTime, None))
  }

  override def postStop(): Unit = {
    super.postStop()

    reconnectTimer foreach (_.cancel())

    updateSubscribers(WebSocketShutdown)
  }

  private def nextReconnectTime(currReconnTime: FiniteDuration): FiniteDuration = {
    val newTime = currReconnTime * 1.5
    (if (newTime > config.maxReconnectTime)
      config.maxReconnectTime.toCoarsest
    else
      newTime.toCoarsest).asInstanceOf[FiniteDuration]
  }

  private def scheduleReconnectTry(currReconnTime: FiniteDuration, data: fsm.Data) = {
    updateSubscribers(WebSocketDisconnected)

    logger.debug(s"Trying to connect in ${currReconnTime.toMillis.millis.toCoarsest}")
    reconnectTimer = Some(context.system.scheduler.scheduleOnce(currReconnTime, self, TryToConnect))
    context become disconnected(data.copy(reconnectTime = nextReconnectTime(currReconnTime), wsActor = None))
  }

  private def updateSubscribers(msg: SocketManagerEvent) = {
    subscribers foreach (_ ! msg)
  }

  private def addSubscriber(currState: SocketManagerEvent) = {
    subscribers += sender()
    context watch sender()
    sender() ! currState
  }

  private def handleSubs(data: fsm.Data): Receive = {
    case SubscribeToEvents ⇒
      addSubscriber(data.wsActor.fold[SocketManagerEvent](WebSocketDisconnected)(WebSocketConnected))

    case JsTerminated(act) if subscribers(act) ⇒ subscribers -= act
  }

  def receive = JsActor.emptyBehavior

  def disconnected(data: fsm.Data): Receive = handleSubs(data) orElse {
    case TryToConnect ⇒
      val wsActor = context.actorOf(config.webSocketActorProps(config.wsUrl, config.clientBridgeActorProps(bridgeProtocol)), wsActorName.next())
      context watch wsActor
      context become disconnected(data.copy(wsActor = Some(wsActor)))

    case JsTerminated(actor) ⇒
      if (data.wsActor contains actor) {
        logger.trace("failed to connect")

        scheduleReconnectTry(data.reconnectTime, data)
      }

    case Connected ⇒
      logger.trace("Connected")
      data.wsActor map WebSocketConnected foreach updateSubscribers
      context become connected(data.copy(reconnectTime = config.initialReconnectTime))
  }

  def connected(data: fsm.Data): Receive = handleSubs(data) orElse {
    case JsTerminated(actor) ⇒
      if (data.wsActor contains actor) {
        logger.trace("Disconnected")

        scheduleReconnectTry(config.initialReconnectTime, data)
      }
  }
}
