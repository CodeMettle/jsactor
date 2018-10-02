/*
 * WebSocketActor.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.client

import org.scalajs.dom

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import jsactor.bridge.client.WebSocketActor.InternalMessages.SendPickledMessageThroughWebsocket
import jsactor.bridge.client.WebSocketActor.Messages.{IdentifyServerActor, MessageReceived, SendMessageToServer}
import jsactor.bridge.client.WebSocketActor.WebSocketSendable._
import jsactor.bridge.client.WebSocketActor.{OnClose, OnError, OnMessage, OnOpen}
import scala.scalajs.js

/**
 * @author steven
 *
 */
object WebSocketActor {
  def props(wsUrl: String, clientBridgeActorProps: Props) =
    Props(new WebSocketActor(wsUrl, clientBridgeActorProps))

  object Messages {
    case class MessageReceived(data: Any)

    case class SendMessageToServer(serverPath: String, message: Any)

    case class IdentifyServerActor(serverPath: String)
  }

  object InternalMessages {
    case class SendPickledMessageThroughWebsocket[T : WebSocketSendable](msg: T) {
      def send(ws: dom.WebSocket): Unit = ws send msg
    }
  }

  private case class OnError(reason: dom.ErrorEvent)
  private case class OnOpen(evt: dom.Event)
  private case class OnClose(evt: dom.CloseEvent)
  private case class OnMessage(evt: dom.MessageEvent)

  trait WebSocketSendable[T] {
    def send(ws: dom.WebSocket, msg: T): Unit
  }

  object WebSocketSendable {
    implicit object StrWSS extends WebSocketSendable[String] {
      override def send(ws: dom.WebSocket, msg: String): Unit = ws send msg
    }
    implicit object BlobWSS extends WebSocketSendable[dom.Blob] {
      override def send(ws: dom.WebSocket, msg: dom.Blob): Unit = ws send msg
    }
    implicit object TypArrWSS extends WebSocketSendable[js.typedarray.ArrayBuffer] {
      override def send(ws: dom.WebSocket, msg: js.typedarray.ArrayBuffer): Unit = ws send msg
    }
    implicit object ByteArrWSS extends WebSocketSendable[Array[Byte]] {
      override def send(ws: dom.WebSocket, msg: Array[Byte]): Unit = {
        import scala.scalajs.js.typedarray._

        ws send msg.toTypedArray.buffer
      }
    }
    implicit class WSSWebSocket(val ws: dom.WebSocket) extends AnyVal {
      def send[T : WebSocketSendable](msg: T): Unit = implicitly[WebSocketSendable[T]].send(ws, msg)
    }
  }
}

//noinspection ActorMutableStateInspection
class WebSocketActor(wsUrl: String, clientBridgeActorProps: Props) extends Actor with ActorLogging {
  private var webSocketOpt = Option.empty[dom.WebSocket]

  private var bridgeActor = Option.empty[ActorRef]

  override def preStart(): Unit = {
    super.preStart()

    log.debug(s"Attempting to connect to $wsUrl")

    val webSocket = new dom.WebSocket(wsUrl)
    webSocket.binaryType = "arraybuffer"
    webSocket.onerror = (evt: dom.ErrorEvent) ⇒ self ! OnError(evt)
    webSocket.onopen = (evt: dom.Event) ⇒ self ! OnOpen(evt)
    webSocket.onclose = (evt: dom.CloseEvent) ⇒ self ! OnClose(evt)
    webSocket.onmessage = (evt: dom.MessageEvent) ⇒ self ! OnMessage(evt)

    webSocketOpt = Some(webSocket)
  }

  override def postStop(): Unit = {
    super.postStop()

    log.info("WebSocket shutting down")

    webSocketOpt foreach (_.close())
  }

  override def receive: Receive = {
    case sendMsg: SendMessageToServer ⇒ bridgeActor foreach (_ forward sendMsg)

    case identify: IdentifyServerActor ⇒ bridgeActor foreach (_ forward identify)

    case spm: SendPickledMessageThroughWebsocket[_] ⇒ webSocketOpt foreach spm.send

    case OnOpen(_) ⇒
      log.info("WebSocket connected to {}", wsUrl)

      bridgeActor = Some(context.actorOf(clientBridgeActorProps, "bridgeActor"))

      context.parent ! SocketManager.InternalMessages.Connected

    case OnError(errEvt) ⇒
      log.error("Error: {}", errEvt.message)
      context stop self

    case OnClose(evt) ⇒
      log.warning("Closed: {}", evt.reason)
      context stop self

    case OnMessage(evt) ⇒
      bridgeActor foreach (_ ! MessageReceived(evt.data))
  }
}
