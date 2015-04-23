/*
 * Messages.scala
 *
 * Updated: Apr 23, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.protocol

/**
 * @author steven
 *
 */
case class BridgeId(clientPath: String, serverPath: String)

case class StatusFailure(cause: Throwable)

sealed trait BridgedMessage {
  def bridgeId: BridgeId
  def message: Any
}
case class ClientToServerMessage(bridgeId: BridgeId, message: Any) extends BridgedMessage
case class ServerToClientMessage(bridgeId: BridgeId, message: Any) extends BridgedMessage

sealed trait ProtocolMessage

case class ClientActorTerminated(clientPath: String) extends ProtocolMessage
case class ServerActorTerminated(serverPath: String) extends ProtocolMessage

case class FindServerActor(bridgeId: BridgeId) extends ProtocolMessage

/**
 * Sent in response to FindServerActor
 *
 * @param bridgeId bridge ID sent with FindServerActor request
 */
case class ServerActorFound(bridgeId: BridgeId) extends ProtocolMessage

/**
 * Sent in response to FindServerActor or ClientToServerMessage
 *
 * @param bridgeId bridge ID sent with original message
 */
case class ServerActorNotFound(bridgeId: BridgeId) extends ProtocolMessage
