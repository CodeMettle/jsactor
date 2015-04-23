/*
 * WebSocketActor.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.client

import org.scalajs.dom._

import com.codemettle.weblogging.WebLogging

import jsactor.bridge.client.WebSocketActor.InternalMessages.SendPickledMessageThroughWebsocket
import jsactor.bridge.client.WebSocketActor.Messages.{IdentifyServerActor, SendMessageToServer, MessageReceived}
import jsactor.bridge.client.WebSocketActor.{OnClose, OnError, OnMessage, OnOpen}
import jsactor.{JsActor, JsActorRef, JsProps}
import scala.scalajs.js

/**
 * @author steven
 *
 */
object WebSocketActor {
  def props(wsUrl: String, clientBridgeActorProps: JsProps) = {
    JsProps(new WebSocketActor(wsUrl, clientBridgeActorProps))
  }

  object Messages {
    case class MessageReceived(data: Any)

    case class SendMessageToServer(serverPath: String, message: Any)

    case class IdentifyServerActor(serverPath: String)
  }

  object InternalMessages {
    case class SendPickledMessageThroughWebsocket(msg: js.Any)
  }

  private case class OnError(reason: ErrorEvent)
  private case class OnOpen(evt: Event)
  private case class OnClose(evt: CloseEvent)
  private case class OnMessage(evt: MessageEvent)
}

class WebSocketActor(wsUrl: String, clientBridgeActorProps: JsProps) extends JsActor with WebLogging {
  private var webSocketOpt = Option.empty[WebSocket]

  private var bridgeActor = Option.empty[JsActorRef]

  override def preStart() = {
    super.preStart()

    logger.trace(s"Attempting to connect to $wsUrl")

    val webSocket = new WebSocket(wsUrl)
    webSocket.onerror = (evt: ErrorEvent) ⇒ self ! OnError(evt)
    webSocket.onopen = (evt: Event) ⇒ self ! OnOpen(evt)
    webSocket.onclose = (evt: CloseEvent) ⇒ self ! OnClose(evt)
    webSocket.onmessage = (evt: MessageEvent) ⇒ self ! OnMessage(evt)

    webSocketOpt = Some(webSocket)
  }

  override def postStop() = {
    super.postStop()

    logger.info("WebSocket shutting down")

    webSocketOpt foreach (_.close())
  }

  def receive = {
    case sendMsg: SendMessageToServer ⇒ bridgeActor foreach (_ forward sendMsg)

    case identify: IdentifyServerActor ⇒ bridgeActor foreach (_ forward identify)

    case SendPickledMessageThroughWebsocket(msg) ⇒ webSocketOpt foreach (_ send msg)

    case OnOpen(evt) ⇒
      logger.info("WebSocket connected to", wsUrl)
      logger.trace(evt)

      bridgeActor = Some(context.actorOf(clientBridgeActorProps, "bridgeActor"))

      context.parent ! SocketManager.InternalMessages.Connected

    case OnError(errEvt) ⇒
      logger.error(errEvt)
      context stop self

    case OnClose(evt) ⇒
      logger.warn(evt)
      context stop self

    case OnMessage(evt) ⇒
      logger.trace(evt)
      bridgeActor foreach (_ ! MessageReceived(evt.data))
  }
}
