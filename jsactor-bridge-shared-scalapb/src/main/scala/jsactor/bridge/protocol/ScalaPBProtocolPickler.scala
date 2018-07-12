/*
 * ScalaPBProtocolPickler.scala
 *
 * Updated: Jul 12, 2018
 *
 * Copyright (c) 2018, CodeMettle
 */
package jsactor.bridge.protocol

import com.google.protobuf.ByteString

import jsactor.bridge.protocol.PBBridgedOrProtocolMessage.PBBridgedOrProtocolMessageTypeMapper

/**
  * Created by steven on 7/12/2018.
  */
private[bridge] class ScalaPBProtocolPickler(implicit
                                             bridgeProtocol: ScalaPBBridgeProtocol) extends
  ProtocolPickler[Array[Byte]] {
  private def convert(bid: BridgeId): Option[PBBridgeId] = Some(PBBridgeId(bid.clientPath, bid.serverPath))
  private def convert(bid: PBBridgeId): BridgeId = BridgeId(bid.clientPath, bid.serverPath)

  private def convert(obj: ProtocolMessage): PBBridgedOrProtocolMessageMessage =
    PBBridgedOrProtocolMessageTypeMapper.toBase(obj match {
      case ClientActorTerminated(clientPath) ⇒ PBClientActorTerminated(clientPath)
      case ServerActorTerminated(serverPath) ⇒ PBServerActorTerminated(serverPath)
      case FindServerActor(bridgeId) ⇒ PBFindServerActor(convert(bridgeId))
      case ServerActorFound(bridgeId) ⇒ PBServerActorFound(convert(bridgeId))
      case ServerActorNotFound(bridgeId) ⇒ PBServerActorNotFound(convert(bridgeId))
    })

  private def convert(bm: BridgedMessage): PBBridgedOrProtocolMessageMessage =
    PBBridgedOrProtocolMessageTypeMapper.toBase(bm match {
      case ClientToServerMessage(bridgeId, message) ⇒
        PBClientToServerMessage(convert(bridgeId), ByteString copyFrom bridgeProtocol.pickle(message))

      case ServerToClientMessage(bridgeId, message) ⇒
        PBServerToClientMessage(convert(bridgeId), ByteString copyFrom bridgeProtocol.pickle(message))
    })

  override def pickle(obj: ProtocolMessage): Array[Byte] = convert(obj).toByteArray
  override def pickle(bm: BridgedMessage): Array[Byte] = convert(bm).toByteArray

  override def unpickle(arr: Array[Byte]): Any = {
    PBBridgedOrProtocolMessageTypeMapper.toCustom(PBBridgedOrProtocolMessageMessage.parseFrom(arr)) match {
      case PBBridgedOrProtocolMessage.Empty ⇒ sys.error("Unknown type")
      case cts: PBClientToServerMessage ⇒
        ClientToServerMessage(convert(cts.getBridgeId), bridgeProtocol.unpickle(cts.message.toByteArray))
      case stc: PBServerToClientMessage ⇒
        ServerToClientMessage(convert(stc.getBridgeId), bridgeProtocol.unpickle(stc.message.toByteArray))
      case PBClientActorTerminated(clientPath) ⇒ ClientActorTerminated(clientPath)
      case PBServerActorTerminated(serverPath) ⇒ ServerActorTerminated(serverPath)
      case fsa: PBFindServerActor ⇒ FindServerActor(convert(fsa.getBridgeId))
      case saf: PBServerActorFound ⇒ ServerActorFound(convert(saf.getBridgeId))
      case sanf: PBServerActorNotFound ⇒ ServerActorNotFound(convert(sanf.getBridgeId))
    }
  }
}
