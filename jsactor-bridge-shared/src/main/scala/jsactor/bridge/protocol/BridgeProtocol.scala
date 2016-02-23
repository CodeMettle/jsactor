/*
 * BridgeProtocol.scala
 *
 * Updated: Apr 23, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor.bridge.protocol

import scala.annotation.implicitNotFound

/**
 * @author steven
 *
 */
@implicitNotFound("Need an implicit BridgeProtocol in scope, consider creating an implicit object extending BridgeProtocol")
trait BridgeProtocol[PickleTo] {
  def pickle(obj: Any): PickleTo

  def unpickle(json: PickleTo): Any
}
