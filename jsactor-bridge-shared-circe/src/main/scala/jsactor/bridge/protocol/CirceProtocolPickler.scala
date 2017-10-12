/*
 * CirceProtocolPickler.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.protocol

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import jsactor.bridge.protocol.CirceProtocolPickler.{ctsPrefix, stcPrefix}

/**
  * @author steven
  *
  */
private[bridge] object CirceProtocolPickler {
  private val ctsPrefix = "__ctsBridge__::"
  private val stcPrefix = "__stcBridge__::"
}

private[bridge] class CirceProtocolPickler(implicit bridgeProtocol: CirceBridgeProtocol)
  extends ProtocolPickler[String] {

  private implicit val ctsEncoder: Encoder[ClientToServerMessage] = Encoder.instance[ClientToServerMessage] {
    cts ⇒ pickleBridgedMsg(cts)
  }

  private implicit val stcEncoder: Encoder[ServerToClientMessage] = Encoder.instance[ServerToClientMessage] {
    stc ⇒ pickleBridgedMsg(stc)
  }

  private implicit val ctsDecoder: Decoder[ClientToServerMessage] =
    Decoder.instance(c ⇒ unpickleBridgedMsg(c, ClientToServerMessage.apply))

  private implicit val stcDecoder: Decoder[ServerToClientMessage] =
    Decoder.instance(c ⇒ unpickleBridgedMsg(c, ServerToClientMessage.apply))

  private def pickleBridgedMsg(bm: BridgedMessage): Json = {
    Encoder[(BridgeId, Json)].apply(bm.bridgeId → bridgeProtocol.pickleJs(bm.message))
  }

  private def unpickleBridgedMsg[PM <: BridgedMessage](jsC: HCursor, ctor: (BridgeId, Any) ⇒ PM): Decoder.Result[PM] = {
    jsC.as[(BridgeId, Json)].right flatMap {
      case (bid, jsVal) ⇒ bridgeProtocol.unpickleJs(jsVal).right.map(msg ⇒ ctor(bid, msg))
    }
  }

  override def pickle(obj: ProtocolMessage): String = obj.asJson.noSpaces
  override def pickle(bm: BridgedMessage): String = bm match {
    case cts: ClientToServerMessage ⇒ ctsPrefix + cts.asJson.noSpaces
    case stc: ServerToClientMessage ⇒ stcPrefix + stc.asJson.noSpaces
  }

  override def unpickle(str: String): Any = {
    (if (str startsWith ctsPrefix)
      decode[ClientToServerMessage](str substring ctsPrefix.length)
    else if (str startsWith stcPrefix)
      decode[ServerToClientMessage](str substring stcPrefix.length)
    else
      decode[ProtocolMessage](str)).valueOrThrow
  }
}
