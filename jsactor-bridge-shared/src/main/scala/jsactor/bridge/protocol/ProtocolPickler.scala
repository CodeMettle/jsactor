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
private[bridge] trait ProtocolPickler[PickleTo] {
  def pickle(obj: ProtocolMessage): PickleTo
  def pickle(bm: BridgedMessage): PickleTo

  def unpickle(str: PickleTo): Any
}
