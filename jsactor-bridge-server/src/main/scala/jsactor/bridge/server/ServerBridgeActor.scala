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
import jsactor.bridge.server.ServerBridgeActor.{SendMessageToClient, ClientActorProxy}
import scala.concurrent.{Promise, Future}
import scala.util.{Success, Failure, Try}

/**
 * @author steven
 *
 */
object ServerBridgeActor {
  def props(clientWebSocket: ActorRef)(implicit bridgeProtocol: BridgeProtocol) = {
    Props(new ServerBridgeActor(clientWebSocket))
  }

  private class ClientActorProxy(clientPath: String) extends Actor with ActorLogging {
    override def preStart(): Unit = {
      super.preStart()

      log.debug("{} created for clientActor {}", self.path, clientPath)
    }

    def receive = {
      case msg ⇒ context.parent ! SendMessageToClient(clientPath, sender().path, sender(), msg)
    }
  }

  private object ClientActorProxy {
    def props(clientPath: String) = {
      Props(new ClientActorProxy(clientPath))
    }
  }

  private case class SendMessageToClient(clientPath: String, serverPath: ActorPath, serverActor: ActorRef, message: Any)
}

class ServerBridgeActor(clientWebSocket: ActorRef)(implicit bridgeProtocol: BridgeProtocol) extends Actor with ActorLogging {
  private val protocolPickler = new ProtocolPickler

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

  private def sendMessageToClient(pm: ProtocolMessage) = {
    clientWebSocket ! protocolPickler.pickle(pm)
  }

  private def sendMessageToClient(clientPath: String, serverPath: String, message: Any) = {
    val msg = message match {
      case Status.Failure(t) ⇒ StatusFailure(t)
      case _ ⇒ message
    }

    val stc = ServerToClientMessage(BridgeId(clientPath, serverPath), msg)

    Try(protocolPickler.pickle(stc)) match {
      case Failure(t) ⇒ log.error(t, "Error pickling {}", stc)

      case Success(json) ⇒ clientWebSocket ! json
    }
  }

  private def fulfillOutstandingIdentify(serverPath: String, actorOpt: Option[ActorRef]) = {
    if (!serverActors.contains(serverPath)) {
      actorOpt foreach (serverActor ⇒ {
        context watch serverActor
        serverActors += (serverPath → serverActor)
      })
    }

    outstandingIdentifies get serverPath foreach (_ success actorOpt)
    outstandingIdentifies -= serverPath
  }

  def receive = {
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

    case json: String ⇒ Try(protocolPickler.unpickle(json)) match {
      case Failure(t) ⇒ log.error(t, "Error unpickling {}", json)

      case Success(msg) ⇒ msg match {
        case ClientToServerMessage(bridgeId, message) ⇒
          import context.dispatcher

          val msg = message match {
            case StatusFailure(t) ⇒ Status.Failure(t)
            case _ ⇒ message
          }

          val clientProxy = getClientProxy(bridgeId)
          getServerActor(bridgeId) onSuccess {
            case Some(serverActor) ⇒ serverActor.tell(msg, clientProxy)

            case None ⇒ sendMessageToClient(ServerActorNotFound(bridgeId))
          }

        case FindServerActor(bridgeId) ⇒
          import context.dispatcher

          getServerActor(bridgeId) onSuccess {
            case Some(serverActor) ⇒ sendMessageToClient(ServerActorFound(bridgeId))

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
