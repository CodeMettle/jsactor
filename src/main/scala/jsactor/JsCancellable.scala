/*
 * JsCancellable.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

/**
 * @author steven
 *
 */
trait JsCancellable {
    /**
     * Cancels this Cancellable and returns true if that was successful.
     * If this cancellable was (concurrently) cancelled already, then this method
     * will return false although isCancelled will return true.
     *
     * Java & Scala API
     */
    def cancel(): Boolean

    def isCancelled: Boolean
}
