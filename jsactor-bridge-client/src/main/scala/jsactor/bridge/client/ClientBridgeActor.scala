/*
 * ClientBridgeActor.scala
 *
 * Updated: Apr 23, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.client

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props, Status, Terminated}
import jsactor.bridge.client.ClientBridgeActor.{SendMessageToServer, ServerActorProxy}
import jsactor.bridge.client.WebSocketActor.WebSocketSendable
import jsactor.bridge.protocol._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
 * @author steven
 *
 */
object ClientBridgeActor {
  private class ServerActorProxy(serverPath: String) extends Actor with ActorLogging {
    override def preStart(): Unit = {
      super.preStart()

      log.debug(s"${self.path} created for serverActor $serverPath")
    }

    override def receive: Receive = {
      case msg ⇒ context.parent ! SendMessageToServer(sender().path, serverPath, sender(), msg)
    }
  }

  private object ServerActorProxy {
    def props(serverPath: String) = Props(new ServerActorProxy(serverPath))
  }

  private case class SendMessageToServer(clientPath: ActorPath, serverPath: String, clientActor: ActorRef, message: Any)
}

//noinspection ActorMutableStateInspection
trait ClientBridgeActor[PickleTo, RecvType] extends Actor with ActorLogging {
  import scala.language.implicitConversions
  protected implicit def pickleWSS: WebSocketSendable[PickleTo]
  protected implicit def recvCT: ClassTag[RecvType]
  protected implicit def recvToPickleFmt(r: RecvType): PickleTo

  implicit def bridgeProtocol: BridgeProtocol[PickleTo]

  protected def newProtocolPickler: ProtocolPickler[PickleTo]

  private val protocolPickler = newProtocolPickler

  private var serverProxies = Map.empty[String, ActorRef]

  private var clientActors = Map.empty[String, ActorRef]

  private def getServerProxy(bridgeId: BridgeId): ActorRef = serverProxies.getOrElse(bridgeId.serverPath, {
    val actor = context.actorOf(ServerActorProxy.props(bridgeId.serverPath))
    serverProxies += (bridgeId.serverPath → actor)
    actor
  })

  private def sendMessageToServer(pm: ProtocolMessage): Unit = {
    context.parent ! WebSocketActor.InternalMessages.SendPickledMessageThroughWebsocket(protocolPickler pickle pm)
  }

  private def sendMessageToServer(clientPath: String, serverPath: String, message: Any): Unit = {
    val msg = message match {
      case Status.Failure(t) ⇒ StatusFailure(t)
      case _ ⇒ message
    }

    val cts = ClientToServerMessage(BridgeId(clientPath, serverPath), msg)

    Try(protocolPickler.pickle(cts)) match {
      case Failure(t) ⇒ log.error(s"Error pickling $cts: $t")

      case Success(json) ⇒
        context.parent ! WebSocketActor.InternalMessages.SendPickledMessageThroughWebsocket(json)
    }
  }

  private def registerClient(clientActor: ActorRef): Unit = {
    val clientPath = clientActor.path.toString
    if (!clientActors.contains(clientPath)) {
      context watch clientActor
      clientActors += (clientPath → clientActor)
    }
  }

  override def receive: Receive = {
    case Terminated(deadClientActor) ⇒
      clientActors = (Map.empty[String, ActorRef] /: clientActors) {
        case (acc, (clientPath, clientActor)) if clientActor == deadClientActor ⇒
          sendMessageToServer(ClientActorTerminated(clientPath))
          acc

        case (acc, e) ⇒ acc + e
      }

    case SendMessageToServer(clientPath, serverPath, clientActor, message) ⇒
      registerClient(clientActor)
      sendMessageToServer(clientPath.toString, serverPath, message)

    case WebSocketActor.Messages.SendMessageToServer(serverPath, message) ⇒
      registerClient(sender())
      sendMessageToServer(sender().path.toString, serverPath, message)

    case WebSocketActor.Messages.IdentifyServerActor(serverPath) ⇒
      registerClient(sender())
      sendMessageToServer(FindServerActor(BridgeId(sender().path.toString, serverPath)))

    case WebSocketActor.Messages.MessageReceived(data) ⇒ data match {
      case json: RecvType ⇒ Try(protocolPickler.unpickle(json)) match {
        case Failure(t) ⇒ log.error(s"Error unpickling $json: $t")

        case Success(msg) ⇒ msg match {
          case ServerToClientMessage(bridgeId, message) ⇒
            clientActors get bridgeId.clientPath match {
              case None ⇒ log.warning(s"Dropping $msg since client actor seems to have died")

              case Some(clientActor) ⇒
                val msg = message match {
                  case StatusFailure(t) ⇒ Status.Failure(t)
                  case _ ⇒ message
                }

                clientActor.tell(msg, getServerProxy(bridgeId))
            }

          case ServerActorTerminated(serverPath) ⇒
            serverProxies get serverPath foreach context.stop
            serverProxies -= serverPath

          case saf@ServerActorFound(bridgeId) ⇒ clientActors get bridgeId.clientPath foreach (clientActor ⇒ {
            clientActor.tell(saf, getServerProxy(bridgeId))
          })

          case sanf@ServerActorNotFound(bridgeId) ⇒ clientActors get bridgeId.clientPath foreach (_ ! sanf)

          case _ ⇒ log.warning(s"Unknown message: $msg")
        }
      }

      case _ ⇒ log.error(s"Don't know how to decode $data")
    }
  }
}
