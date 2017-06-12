/*
 * BooPickleProtocolPickler.scala
 *
 * Updated: Mar 1, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.protocol

import java.nio.ByteBuffer

import boopickle.Default._

import jsactor.bridge.protocol.BooPickleProtocolPickler.{bmId, pmId}

/**
  * @author steven
  *
  */
private[bridge] object BooPickleProtocolPickler {
  private val bmId = 0.toByte
  private val pmId = 1.toByte
}

private[bridge] class BooPickleProtocolPickler(implicit bridgeProtocol: BooPickleBridgeProtocol) extends ProtocolPickler[Array[Byte]] {
  private implicit val ctsPickler = transformPickler[ClientToServerMessage, (BridgeId, ByteBuffer)](
    e ⇒ ClientToServerMessage(e._1, bridgeProtocol.unpickleBB(e._2).get))(
    cts ⇒ (cts.bridgeId, bridgeProtocol.pickleBB(cts.message)))
  private implicit val stcPickler = transformPickler[ServerToClientMessage, (BridgeId, ByteBuffer)](
    e ⇒ ServerToClientMessage(e._1, bridgeProtocol.unpickleBB(e._2).get))(
    stc ⇒ (stc.bridgeId, bridgeProtocol.pickleBB(stc.message)))
  private implicit val bmPickler =
    compositePickler[BridgedMessage]
      .addConcreteType[ClientToServerMessage]
      .addConcreteType[ServerToClientMessage]

  override def pickle(obj: ProtocolMessage): Array[Byte] = bb2arr(Pickle.intoBytes(pmId → Pickle.intoBytes(obj)))
  override def pickle(bm: BridgedMessage): Array[Byte] = bb2arr(Pickle.intoBytes(bmId → Pickle.intoBytes(bm)))

  override def unpickle(arr: Array[Byte]): Any = {
    val (typ, bb) = Unpickle[(Byte, ByteBuffer)].fromBytes(ByteBuffer wrap arr)
    typ match {
      case `bmId` ⇒ Unpickle[BridgedMessage].fromBytes(bb)
      case `pmId` ⇒ Unpickle[ProtocolMessage].fromBytes(bb)
      case _ ⇒ sys.error(s"Invalid type $typ")
    }
  }
}
