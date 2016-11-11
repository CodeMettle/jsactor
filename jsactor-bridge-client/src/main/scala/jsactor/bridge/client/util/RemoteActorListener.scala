/*
 * RemoteActorListener.scala
 *
 * Updated: Apr 15, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.client.util

import jsactor._
import jsactor.bridge.client.util.UntypedRemoteActorListener.TryConnect
import jsactor.bridge.client.{SocketManager, WebSocketActor, WebSocketManager}
import jsactor.bridge.protocol.{ServerActorFound, ServerActorNotFound}
import jsactor.logging.JsActorLogging
import scala.concurrent.duration._

/**
 * @author steven
 *
 */
object UntypedRemoteActorListener {
  private case object TryConnect
}

trait UntypedRemoteActorListener extends JsActor with JsStash with JsActorLogging {
  def actorPath: String
  def wsManager: JsActorRef
  def onConnect(serverActor: JsActorRef): Unit
  def whenConnected(serverActor: JsActorRef): Receive
  def retryTimeout: FiniteDuration = 2.seconds
  protected def onDisconnect() = {}

  private var websocket = Option.empty[JsActorRef]

  private var serverActor = Option.empty[JsActorRef]

  override def preStart() = {
    super.preStart()

    wsManager ! SocketManager.Events.SubscribeToEvents
  }

  final def receive = {
    case SocketManager.Events.WebSocketConnected(socket) ⇒
      websocket = Some(socket)
      self ! TryConnect

    case TryConnect ⇒
      context setReceiveTimeout retryTimeout
      websocket foreach (_ ! WebSocketActor.Messages.IdentifyServerActor(actorPath))

    case JsReceiveTimeout ⇒
      log.warn("No response from server when trying to send message to ", actorPath)
      self ! TryConnect

    case ServerActorNotFound(_) ⇒
      log.warn("No actor returned from server for ", actorPath)

    case ServerActorFound(_) ⇒
      val act = sender()
      log.trace("Got actor ", act.toString, " for ", actorPath)
      context watch act
      context setReceiveTimeout Duration.Undefined
      onConnect(act)
      context become (listenForDisconnects orElse whenConnected(act))
      unstashAll()

    case SocketManager.Events.WebSocketDisconnected | SocketManager.Events.WebSocketShutdown ⇒
      context setReceiveTimeout Duration.Undefined

      websocket = None

    case msg ⇒ stash()
  }

  private def listenForDisconnects: Receive = {
    case JsTerminated(act) if serverActor contains act ⇒
      log.trace("Server actor ", act.toString, " terminated")
      serverActor = None
      context become receive
      self ! TryConnect
      onDisconnect()

    case SocketManager.Events.WebSocketDisconnected | SocketManager.Events.WebSocketShutdown ⇒
      log.trace("WebSocket disconnected")
      serverActor = None
      websocket = None
      context become receive
      onDisconnect()
  }
}

trait RemoteActorListener extends UntypedRemoteActorListener {
  def webSocketManager: WebSocketManager
  final override def wsManager = webSocketManager.socketManager
}
