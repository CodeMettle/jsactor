/*
 * RippedFromAkka.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import java.net.{URISyntaxException, URI}

import scala.annotation.tailrec
import scala.collection.immutable

/**
 * @author steven
 *
 */
object RippedFromAkka {
    final val base64chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+~"

    @tailrec
    def base64(l: Long, sb: java.lang.StringBuilder = new java.lang.StringBuilder("$")): String = {
        sb append base64chars.charAt(l.toInt & 63)
        val next = l >>> 6
        if (next == 0) sb.toString
        else base64(next, sb)
    }


    private[jsactor] trait PathUtils {
        protected def split(s: String, fragment: String): List[String] = {
            @tailrec
            def rec(pos: Int, acc: List[String]): List[String] = {
                val from = s.lastIndexOf('/', pos - 1)
                val sub = s.substring(from + 1, pos)
                val l =
                    if ((fragment ne null) && acc.isEmpty) sub + "#" + fragment :: acc
                    else sub :: acc
                if (from == -1) l else rec(from, l)
            }
            rec(s.length, Nil)
        }
    }

    /**
     * Extractor for so-called “relative actor paths” as in “relative URI”, not in
     * “relative to some actor”. Examples:
     *
     *  * "grand/child"
     *  * "/user/hello/world"
     */
    object RelativeActorPath extends PathUtils {
        def unapply(addr: String): Option[immutable.Seq[String]] = {
            try {
                val uri = new URI(addr)
                if (uri.isAbsolute) None
                else Some(split(uri.getRawPath, uri.getRawFragment))
            } catch {
                case _: URISyntaxException ⇒ None
            }
        }
    }

    /**
     * This object serves as extractor for Scala and as address parser for Java.
     */
    object AddressFromURIString {
        def unapply(addr: String): Option[JsAddress] = try unapply(new URI(addr)) catch { case _: URISyntaxException ⇒ None }

        def unapply(uri: URI): Option[JsAddress] =
            if (uri eq null) None
            else if (uri.getScheme == null || (uri.getUserInfo == null && uri.getHost == null)) None
            else if (uri.getUserInfo == null) { // case 1: “akka://system”
                if (uri.getPort != -1) None
                else Some(JsAddress(uri.getScheme, uri.getHost))
            } else { // case 2: “akka://system@host:port”
                if (uri.getHost == null || uri.getPort == -1) None
                else Some(
                    if (uri.getUserInfo == null) JsAddress(uri.getScheme, uri.getHost)
                    else JsAddress(uri.getScheme, uri.getUserInfo))
            }

        /**
         * Try to construct an Address from the given String or throw a java.net.MalformedURLException.
         */
        def apply(addr: String): JsAddress = addr match {
            case AddressFromURIString(address) ⇒ address
            case _                             ⇒ throw new Exception(s"Malformed URL: $addr")
        }
    }

    /**
     * Given an ActorPath it returns the Address and the path elements if the path is well-formed
     */
    object ActorPathExtractor extends PathUtils {
        def unapply(addr: String): Option[(JsAddress, immutable.Iterable[String])] =
            try {
                val uri = new URI(addr)
                uri.getRawPath match {
                    case null ⇒ None
                    case path ⇒ AddressFromURIString.unapply(uri).map((_, split(path, uri.getRawFragment).drop(1)))
                }
            } catch {
                case _: URISyntaxException ⇒ None
            }
    }

}
