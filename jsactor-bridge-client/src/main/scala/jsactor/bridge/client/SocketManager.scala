/*
 * SocketManager.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.client

import akka.actor._
import akka.util.Helpers
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
  trait Config[BP <: BridgeProtocol[PickleTo], PickleTo] {
    def wsUrl: String
    def clientBridgeActorProps: (BP) ⇒ Props
    def webSocketActorProps: (String, Props) ⇒ Props
    def initialReconnectTime: FiniteDuration
    def maxReconnectTime: FiniteDuration
  }

  private object fsm {
    case class Data(reconnectTime: FiniteDuration, wsActor: Option[ActorRef])
  }

  object InternalMessages {
    case object Connected
  }

  object Events {
    case object SubscribeToEvents

    sealed trait SocketManagerEvent
    case class WebSocketConnected(socket: ActorRef) extends SocketManagerEvent
    case object WebSocketDisconnected extends SocketManagerEvent
    case object WebSocketShutdown extends SocketManagerEvent
  }

  private case object TryToConnect
}

//noinspection ActorMutableStateInspection
trait SocketManager[BP <: BridgeProtocol[PickleTo], PickleTo] extends Actor with ActorLogging {
  import context.dispatcher

  def config: Config[BP, PickleTo]
  implicit def bridgeProtocol: BP

  private val wsActorName = Iterator from 0 map (i ⇒ s"ws${Helpers.base64(i)}")

  private var reconnectTimer = Option.empty[Cancellable]

  private var subscribers = Set.empty[ActorRef]

  override def preStart(): Unit = {
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

  private def scheduleReconnectTry(currReconnTime: FiniteDuration, data: fsm.Data): Unit = {
    updateSubscribers(WebSocketDisconnected)

    log.debug(s"Trying to connect in ${currReconnTime.toMillis.millis.toCoarsest}")
    reconnectTimer = Some(context.system.scheduler.scheduleOnce(currReconnTime, self, TryToConnect))
    context become disconnected(data.copy(reconnectTime = nextReconnectTime(currReconnTime), wsActor = None))
  }

  private def updateSubscribers(msg: SocketManagerEvent): Unit = {
    subscribers foreach (_ ! msg)
  }

  private def addSubscriber(currState: SocketManagerEvent): Unit = {
    subscribers += sender()
    context watch sender()
    sender() ! currState
  }

  private def handleSubs(data: fsm.Data): Receive = {
    case SubscribeToEvents ⇒
      addSubscriber(data.wsActor.fold[SocketManagerEvent](WebSocketDisconnected)(WebSocketConnected))

    case Terminated(act) if subscribers(act) ⇒ subscribers -= act
  }

  override def receive: Receive = Actor.emptyBehavior

  private def disconnected(data: fsm.Data): Receive = handleSubs(data) orElse {
    case TryToConnect ⇒
      val props = config.webSocketActorProps(config.wsUrl, config.clientBridgeActorProps(bridgeProtocol))
      val wsActor = context.actorOf(props, wsActorName.next())
      context watch wsActor
      context become disconnected(data.copy(wsActor = Some(wsActor)))

    case Terminated(actor) ⇒
      if (data.wsActor contains actor) {
        log.debug("failed to connect")

        scheduleReconnectTry(data.reconnectTime, data)
      }

    case Connected ⇒
      log.debug("Connected")
      data.wsActor map WebSocketConnected foreach updateSubscribers
      context become connected(data.copy(reconnectTime = config.initialReconnectTime))
  }

  private def connected(data: fsm.Data): Receive = handleSubs(data) orElse {
    case Terminated(actor) ⇒
      if (data.wsActor contains actor) {
        log.debug("Disconnected")

        scheduleReconnectTry(config.initialReconnectTime, data)
      }
  }
}

trait WebSocketManager {
  def socketManager: ActorRef

  def subscribeToEvents(implicit subscriber: ActorRef): Unit = socketManager ! SocketManager.Events.SubscribeToEvents
}
