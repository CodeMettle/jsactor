/*
 * ProtocolPickler.scala
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
private[bridge] trait ProtocolPickler[JsValue] {
  def pickle(obj: ProtocolMessage): String
  def pickle(bm: BridgedMessage): String

  def unpickle(str: String): Any
}
