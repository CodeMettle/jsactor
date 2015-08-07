/*
 * ProtocolPickler.scala
 *
 * Updated: Apr 23, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.protocol

import upickle._
import upickle.default._

import jsactor.bridge.protocol.ProtocolPickler.{ctsPrefix, stcPrefix}

/**
 * @author steven
 *
 */
private[bridge] object ProtocolPickler {
  private val ctsPrefix = "__ctsBridge__::"
  private val stcPrefix = "__stcBridge__::"
}

private[bridge] class ProtocolPickler(implicit bridgeProtocol: BridgeProtocol) {

  private def pickleBridgedMsg(bm: BridgedMessage): Js.Arr = {
    Js.Arr(writeJs(bm.bridgeId), bridgeProtocol.pickleJs(bm.message))
  }

  private def unpickleBridgedMsg[PM <: BridgedMessage](jsVal: Js.Value, ctor: (BridgeId, Any) ⇒ PM): PM = {
    jsVal match {
      case jsArr: Js.Arr ⇒
        if (jsArr.value.size != 2)
          throw Invalid.Data(jsArr, "Expected 2 elements")

        val bid = readJs[BridgeId](jsArr.value(0))
        val msg = bridgeProtocol.unpickleJs(jsArr.value(1))

        ctor(bid, msg)

      case _ ⇒ throw Invalid.Data(jsVal, "Expected a JSON array")
    }
  }

  private implicit val ctsWriter = Writer[ClientToServerMessage] {
    case cts ⇒ pickleBridgedMsg(cts)
  }

  private implicit val stcWriter = Writer[ServerToClientMessage] {
    case stc ⇒ pickleBridgedMsg(stc)
  }

  private implicit val ctsReader = Reader[ClientToServerMessage] {
    case jsVal ⇒ unpickleBridgedMsg(jsVal, ClientToServerMessage.apply)
  }

  private implicit val stcReader = Reader[ServerToClientMessage] {
    case jsVal ⇒ unpickleBridgedMsg(jsVal, ServerToClientMessage.apply)
  }

  def pickle[PM <: ProtocolMessage : Writer](obj: PM): String = write(obj)
  def pickle(bm: BridgedMessage): String = bm match {
    case cts: ClientToServerMessage ⇒ ctsPrefix + write(cts)
    case stc: ServerToClientMessage ⇒ stcPrefix + write(stc)
  }

  def unpickle(str: String): Any = {
    if (str startsWith ctsPrefix)
      read[ClientToServerMessage](str substring ctsPrefix.length)
    else if (str startsWith stcPrefix)
      read[ServerToClientMessage](str substring stcPrefix.length)
    else
      read[ProtocolMessage](str)
  }
}
