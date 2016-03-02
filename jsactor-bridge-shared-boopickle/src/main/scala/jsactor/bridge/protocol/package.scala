/*
 * package.scala
 *
 * Updated: Mar 1, 2016
 *
 * Copyright (c) 2016, CodeMettle
 */
package jsactor.bridge

import java.nio.ByteBuffer

/**
  * @author steven
  *
  */
package object protocol {
  def bb2arr(bb: ByteBuffer): Array[Byte] = {
    val ba = new Array[Byte](bb.limit())
    bb.get(ba)
    ba
  }
}
