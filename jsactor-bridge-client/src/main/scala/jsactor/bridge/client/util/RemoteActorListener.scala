/*
 * RemoteActorListener.scala
 *
 * Updated: Apr 15, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.client.util

import akka.actor._
import jsactor.bridge.client.util.UntypedRemoteActorListener.TryConnect
import jsactor.bridge.client.{SocketManager, WebSocketActor, WebSocketManager}
import jsactor.bridge.protocol.{ServerActorFound, ServerActorNotFound}
import scala.concurrent.duration._

/**
 * @author steven
 *
 */
object UntypedRemoteActorListener {
  private case object TryConnect
}

//noinspection ActorMutableStateInspection
trait UntypedRemoteActorListener extends Actor with Stash with ActorLogging {
  def actorPath: String
  def wsManager: ActorRef
  def onConnect(serverActor: ActorRef): Unit
  def whenConnected(serverActor: ActorRef): Receive
  def retryTimeout: FiniteDuration = 2.seconds
  protected def onDisconnect(): Unit = {}

  private var websocket = Option.empty[ActorRef]

  private var serverActor = Option.empty[ActorRef]

  private var hasWatched = Set.empty[ActorRef]

  override def preStart(): Unit = {
    super.preStart()

    wsManager ! SocketManager.Events.SubscribeToEvents
  }

  override final def receive: Receive = {
    case SocketManager.Events.WebSocketConnected(socket) ⇒
      websocket = Some(socket)
      self ! TryConnect

    case TryConnect ⇒
      context setReceiveTimeout retryTimeout
      websocket foreach (_ ! WebSocketActor.Messages.IdentifyServerActor(actorPath))

    case ReceiveTimeout ⇒
      log.warning("No response from server when trying to send message to {}", actorPath)
      self ! TryConnect

    case ServerActorNotFound(_) ⇒
      log.warning("No actor returned from server for {}", actorPath)

    case ServerActorFound(_) ⇒
      val act = sender()
      log.debug("Got actor {} for {}", act, actorPath)
      serverActor = Some(act)
      context watch act
      hasWatched += act
      context setReceiveTimeout Duration.Undefined
      onConnect(act)
      context become (listenForDisconnects orElse whenConnected(act))
      unstashAll()

    case SocketManager.Events.WebSocketDisconnected | SocketManager.Events.WebSocketShutdown ⇒
      context setReceiveTimeout Duration.Undefined

      websocket = None

    case _: Terminated ⇒ // already disconnected from websocketdisconnect

    case _ ⇒ stash()
  }

  private def listenForDisconnects: Receive = {
    case Terminated(act) if serverActor contains act ⇒
      log.debug("Server actor {} terminated", act)
      hasWatched -= act
      serverActor = None
      context become receive
      self ! TryConnect
      onDisconnect()

    case Terminated(act) if hasWatched contains act ⇒ hasWatched -= act

    case SocketManager.Events.WebSocketDisconnected | SocketManager.Events.WebSocketShutdown ⇒
      log.debug("WebSocket disconnected")
      serverActor = None
      websocket = None
      context become receive
      onDisconnect()
  }
}

trait RemoteActorListener extends UntypedRemoteActorListener {
  def webSocketManager: WebSocketManager
  final override def wsManager: ActorRef = webSocketManager.socketManager
}
