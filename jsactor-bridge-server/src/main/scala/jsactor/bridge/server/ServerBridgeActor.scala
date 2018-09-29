/*
 * ServerBridgeActor.scala
 *
 * Updated: Apr 23, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.server

import akka.actor._
import jsactor.bridge.protocol._
import jsactor.bridge.server.ServerBridgeActor.WebSocketSendable._
import jsactor.bridge.server.ServerBridgeActor.{ClientActorProxy, SendMessageToClient, WebSocketSendable}
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
 * @author steven
 *
 */
object ServerBridgeActor {
  private class ClientActorProxy(clientPath: String) extends Actor with ActorLogging {
    override def preStart(): Unit = {
      super.preStart()

      log.debug("{} created for clientActor {}", self.path, clientPath)
    }

    override def receive: Receive = {
      case msg ⇒ context.parent ! SendMessageToClient(clientPath, sender().path, sender(), msg)
    }
  }

  private object ClientActorProxy {
    def props(clientPath: String) = Props(new ClientActorProxy(clientPath))
  }

  private case class SendMessageToClient(clientPath: String, serverPath: ActorPath, serverActor: ActorRef, message: Any)

  trait WebSocketSendable[T] {
    def sendPickledMsg(ws: ActorRef, msg: T)(implicit sender: ActorRef): Unit
  }

  object WebSocketSendable {
    // implicits for 2 types that Play WebSockets can accept by default
    implicit object StrWSS extends WebSocketSendable[String] {
      override def sendPickledMsg(ws: ActorRef, msg: String)(implicit sender: ActorRef): Unit = ws ! msg
    }
    implicit object ArrWSS extends WebSocketSendable[Array[Byte]] {
      override def sendPickledMsg(ws: ActorRef, msg: Array[Byte])(implicit sender: ActorRef): Unit = ws ! msg
    }
    implicit class WSSWebSocket(val ws: ActorRef) extends AnyVal {
      def sendPickledMsg[T : WebSocketSendable](msg: T)(implicit sender: ActorRef): Unit =
        implicitly[WebSocketSendable[T]].sendPickledMsg(ws, msg)
    }
  }
}

//noinspection ActorMutableStateInspection
trait ServerBridgeActor[PickleTo] extends Actor with ActorLogging {
  protected implicit def pickleToCT: ClassTag[PickleTo]
  protected implicit def pickleWSS: WebSocketSendable[PickleTo]
  protected implicit def bridgeProtocol: BridgeProtocol[PickleTo]
  def clientWebSocket: ActorRef
  protected def newProtocolPickler: ProtocolPickler[PickleTo]
  private val protocolPickler = newProtocolPickler

  private var clientProxies = Map.empty[String, ActorRef]

  private var serverActors = Map.empty[String, ActorRef]

  private var outstandingIdentifies = Map.empty[String, Promise[Option[ActorRef]]]

  private def getClientProxy(bridgeId: BridgeId): ActorRef = clientProxies.getOrElse(bridgeId.clientPath, {
    val actor = context.actorOf(ClientActorProxy.props(bridgeId.clientPath))
    clientProxies += (bridgeId.clientPath → actor)
    actor
  })

  private def getServerActor(bridgeId: BridgeId): Future[Option[ActorRef]] = {
    val serverPath = bridgeId.serverPath

    serverActors get serverPath match {
      case saOpt@Some(_) ⇒ Future successful saOpt
      case None ⇒ outstandingIdentifies get serverPath match {
        case Some(p) ⇒ p.future
        case None ⇒
          val p = Promise[Option[ActorRef]]()
          outstandingIdentifies += (serverPath → p)
          context.actorSelection(serverPath) ! Identify(serverPath)
          p.future
      }
    }
  }

  private def sendMessageToClient(pm: ProtocolMessage): Unit = {
    Try(protocolPickler.pickle(pm)) match {
      case Failure(t) ⇒ log.error(t, "Error pickling {}, pp = {}", pm, protocolPickler)

      case Success(json) ⇒ clientWebSocket sendPickledMsg json
    }
  }

  private def sendMessageToClient(clientPath: String, serverPath: String, message: Any): Unit = {
    val msg = message match {
      case Status.Failure(t) ⇒ StatusFailure(t)
      case _ ⇒ message
    }

    val stc = ServerToClientMessage(BridgeId(clientPath, serverPath), msg)

    Try(protocolPickler.pickle(stc)) match {
      case Failure(t) ⇒ log.error(t, "Error pickling {}, pp = {}", stc, protocolPickler)

      case Success(json) ⇒ clientWebSocket sendPickledMsg json
    }
  }

  private def fulfillOutstandingIdentify(serverPath: String, actorOpt: Option[ActorRef]): Unit = {
    if (!serverActors.contains(serverPath)) {
      actorOpt foreach (serverActor ⇒ {
        context watch serverActor
        serverActors += (serverPath → serverActor)
      })
    }

    outstandingIdentifies get serverPath foreach (_ success actorOpt)
    outstandingIdentifies -= serverPath
  }

  /**
    * Subclasses can override this method to intercept, transform or redirect incoming messages.
    *
    * This method will be called only if a message is bound for a valid server actor.
    *
    * @param msg The incoming message.
    * @param clientProxy A proxy to the client actor.
    * @param serverActor The server actor destination.
    */
  protected def dispatchIncomingMessage(msg: Any, clientProxy: ActorRef, serverActor: ActorRef): Unit =
    serverActor.tell(msg, clientProxy)

  override def receive: Receive = {
    case ActorIdentity(serverPath: String, actorOpt) ⇒ fulfillOutstandingIdentify(serverPath, actorOpt)

    case Terminated(deadServerActor) ⇒
      serverActors = (Map.empty[String, ActorRef] /: serverActors) {
        case (acc, (serverPath, serverActor)) if serverActor == deadServerActor ⇒
          sendMessageToClient(ServerActorTerminated(serverPath))
          acc

        case (acc, e) ⇒ acc + e
      }

    case SendMessageToClient(clientPath, serverPath, serverActor, message) ⇒
      val serverPathStr = serverPath.toString
      fulfillOutstandingIdentify(serverPathStr, Some(serverActor))
      sendMessageToClient(clientPath, serverPathStr, message)

    case json: PickleTo ⇒ Try(protocolPickler.unpickle(json)) match {
      case Failure(t) ⇒ log.error(t, "Error unpickling {}", json)

      case Success(msg) ⇒ msg match {
        case ClientToServerMessage(bridgeId, message) ⇒
          import context.dispatcher

          val msg = message match {
            case StatusFailure(t) ⇒ Status.Failure(t)
            case _ ⇒ message
          }

          val clientProxy = getClientProxy(bridgeId)
          getServerActor(bridgeId) foreach {
            case Some(serverActor) ⇒ dispatchIncomingMessage(msg, clientProxy, serverActor)

            case None ⇒ sendMessageToClient(ServerActorNotFound(bridgeId))
          }

        case FindServerActor(bridgeId) ⇒
          import context.dispatcher

          getServerActor(bridgeId) foreach {
            case Some(_) ⇒ sendMessageToClient(ServerActorFound(bridgeId))

            case None ⇒ sendMessageToClient(ServerActorNotFound(bridgeId))
          }

        case ClientActorTerminated(clientPath) ⇒
          clientProxies get clientPath foreach context.stop
          clientProxies -= clientPath

        case _ ⇒ log.warning("Unknown message: {}", msg)
      }
    }
  }
}
