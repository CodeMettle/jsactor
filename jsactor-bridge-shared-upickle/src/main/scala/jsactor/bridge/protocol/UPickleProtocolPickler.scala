/*
 * UPickleProtocolPickler.scala
 *
 * Updated: Feb 19, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge.protocol

import upickle._
import upickle.default._

import jsactor.bridge.protocol.UPickleProtocolPickler.{ctsPrefix, stcPrefix}
import scala.util.{Failure, Try}

/**
 * @author steven
 *
 */
private[bridge] object UPickleProtocolPickler {
  private val ctsPrefix = "__ctsBridge__::"
  private val stcPrefix = "__stcBridge__::"
}

private[bridge] class UPickleProtocolPickler(implicit bridgeProtocol: UPickleBridgeProtocol)
  extends ProtocolPickler[String] {

  private def pickleBridgedMsg(bm: BridgedMessage): Js.Arr = {
    Js.Arr(writeJs(bm.bridgeId), bridgeProtocol.pickleJs(bm.message))
  }

  private def unpickleBridgedMsg[PM <: BridgedMessage](jsVal: Js.Value, ctor: (BridgeId, Any) ⇒ PM): Try[PM] = {
    jsVal match {
      case jsArr: Js.Arr if jsArr.value.size == 2 ⇒
        val bidT = Try(readJs[BridgeId](jsArr.value(0)))
        val msgT = bridgeProtocol.unpickleJs(jsArr.value(1))

        for (bid ← bidT; msg ← msgT) yield ctor(bid, msg)

      case _ ⇒ Failure(Invalid.Data(jsVal, "Expected an array of 2 elements"))
    }
  }

  private implicit val ctsWriter = Writer[ClientToServerMessage] {
    case cts ⇒ pickleBridgedMsg(cts)
  }

  private implicit val stcWriter = Writer[ServerToClientMessage] {
    case stc ⇒ pickleBridgedMsg(stc)
  }

  private implicit val ctsReader = Reader[ClientToServerMessage] {
    case jsVal ⇒ unpickleBridgedMsg(jsVal, ClientToServerMessage.apply).get
  }

  private implicit val stcReader = Reader[ServerToClientMessage] {
    case jsVal ⇒ unpickleBridgedMsg(jsVal, ServerToClientMessage.apply).get
  }

  def pickle(obj: ProtocolMessage): String = write(obj)
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
