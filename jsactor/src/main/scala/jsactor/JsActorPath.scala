/*
 * JsActorPath.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import java.{lang ⇒ jl}

import jsactor.RippedFromAkka.ActorPathExtractor
import scala.annotation.tailrec
import scala.collection.immutable

/**
 * @author steven
 *
 */
case class JsAddress(protocol: String, system: String) {
  /**
   * Returns the canonical String representation of this Address formatted as:
   *
   * <protocol>://<system>@<host>:<port>
   */
  @transient
  override lazy val toString: String = {
    val sb = (new java.lang.StringBuilder(protocol)).append("://").append(system)

    //        if (host.isDefined) sb.append('@').append(host.get)
    //        if (port.isDefined) sb.append(':').append(port.get)

    sb.toString
  }
}

object JsActorPath {
  /**
   * Parse string as actor path; throws java.net.MalformedURLException if unable to do so.
   */
  def fromString(s: String): JsActorPath = s match {
    case ActorPathExtractor(addr, elems) ⇒ JsRootActorPath(addr) / elems
    case _ ⇒ throw new Exception("cannot parse as ActorPath: " + s)
  }

  /** INTERNAL API */
  private[jsactor] final val ValidSymbols = """-_.*$+:@&=,!~';"""

  /**
   * This method is used to validate a path element (Actor Name).
   * Since Actors form a tree, it is addressable using an URL, therefore an Actor Name has to conform to:
   * [[http://www.ietf.org/rfc/rfc2396.txt RFC-2396]].
   *
   * User defined Actor names may not start from a `$` sign - these are reserved for system names.
   */
  final def isValidPathElement(s: String): Boolean = {
    def isValidChar(c: Char): Boolean =
      (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (ValidSymbols.indexOf(c) != -1)

    def isHexChar(c: Char): Boolean =
      (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || (c >= '0' && c <= '9')

    val len = s.length
    def validate(pos: Int): Boolean =
      if (pos < len)
        s.charAt(pos) match {
          case c if isValidChar(c) ⇒ validate(pos + 1)
          case '%' if pos + 2 < len && isHexChar(s.charAt(pos + 1)) && isHexChar(s.charAt(pos + 2)) ⇒ validate(pos + 3)
          case _ ⇒ false
        }
      else true

    len > 0 && s.charAt(0) != '$' && validate(0)
  }

  private[jsactor] final val emptyActorPath: immutable.Iterable[String] = List("")
}

sealed trait JsActorPath extends Comparable[JsActorPath] {
  /**
   * The Address under which this path can be reached; walks up the tree to
   * the RootActorPath.
   */
  def address: JsAddress

  /**
   * The name of the actor that this path refers to.
   */
  def name: String

  /**
   * The path for the parent actor.
   */
  def parent: JsActorPath

  /**
   * Create a new child actor path.
   */
  def /(child: String): JsActorPath

  /**
   * Recursively create a descendant’s path by appending all child names.
   */
  def /(child: Iterable[String]): JsActorPath = (this /: child)((path, elem) ⇒ if (elem.isEmpty) path else path / elem)

  /**
   * Sequence of names for this path from root to this. Performance implication: has to allocate a list.
   */
  def elements: immutable.Iterable[String]

  /**
   * Walk up the tree to obtain and return the RootActorPath.
   */
  def root: JsRootActorPath

  /**
   * String representation of the path elements, excluding the address
   * information. The elements are separated with "/" and starts with "/",
   * e.g. "/user/a/b".
   */
  def toStringWithoutAddress: String = elements.mkString("/", "/", "")

  /**
   * Generate String representation, replacing the Address in the RootActor
   * Path with the given one unless this path’s address includes host and port
   * information.
   */
  def toStringWithAddress(address: JsAddress): String

  /**
   * Generate full String representation including the
   * uid for the actor cell instance as URI fragment.
   * This representation should be used as serialized
   * representation instead of `toString`.
   */
  def toSerializationFormat: String

  /**
   * Generate full String representation including the uid for the actor cell
   * instance as URI fragment, replacing the Address in the RootActor Path
   * with the given one unless this path’s address includes host and port
   * information. This representation should be used as serialized
   * representation instead of `toStringWithAddress`.
   */
  def toSerializationFormatWithAddress(address: JsAddress): String
}

final case class JsRootActorPath(address: JsAddress, name: String = "/") extends JsActorPath {

  override def parent: JsActorPath = this

  override def root: JsRootActorPath = this

  override def /(child: String): JsActorPath = {
    new JsChildActorPath(this, child)
  }

  override def elements: immutable.Iterable[String] = JsActorPath.emptyActorPath

  override val toString: String = address + name

  override val toSerializationFormat: String = toString

  override def toStringWithAddress(addr: JsAddress): String = addr + name

  override def toSerializationFormatWithAddress(addr: JsAddress): String = toStringWithAddress(addr)

  override def compareTo(other: JsActorPath): Int = other match {
    case r: JsRootActorPath ⇒ toString compareTo r.toString // FIXME make this cheaper by comparing address and name in isolation
    case c: JsChildActorPath ⇒ 1
  }

}

final class JsChildActorPath private[jsactor](val parent: JsActorPath, val name: String) extends JsActorPath {
  if (name.indexOf('/') != -1) throw new Exception("/ is a path separator and is not legal in ActorPath names: [%s]" format name)
  if (name.indexOf('#') != -1) throw new Exception("# is a fragment separator and is not legal in ActorPath names: [%s]" format name)

  override def address: JsAddress = root.address

  override def /(child: String): JsActorPath = {
    new JsChildActorPath(this, child)
  }

  override def elements: immutable.Iterable[String] = {
    @tailrec
    def rec(p: JsActorPath, acc: List[String]): immutable.Iterable[String] = p match {
      case r: JsRootActorPath ⇒ acc
      case _ ⇒ rec(p.parent, p.name :: acc)
    }
    rec(this, Nil)
  }

  override def root: JsRootActorPath = {
    @tailrec
    def rec(p: JsActorPath): JsRootActorPath = p match {
      case r: JsRootActorPath ⇒ r
      case _ ⇒ rec(p.parent)
    }
    rec(this)
  }

  override def toString: String = {
    val length = toStringLength
    buildToString(new jl.StringBuilder(length), length, 0, _.toString).toString
  }

  override def toSerializationFormat: String = {
    val length = toStringLength
    val sb = buildToString(new jl.StringBuilder(length + 12), length, 0, _.toString)
    sb.toString
  }

  private def toStringLength: Int = toStringOffset + name.length

  private val toStringOffset: Int = parent match {
    case r: JsRootActorPath ⇒ r.address.toString.length + r.name.length
    case c: JsChildActorPath ⇒ c.toStringLength + 1
  }

  override def toStringWithAddress(addr: JsAddress): String = {
    val diff = addressStringLengthDiff(addr)
    val length = toStringLength + diff
    buildToString(new jl.StringBuilder(length), length, diff, _.toStringWithAddress(addr)).toString
  }

  override def toSerializationFormatWithAddress(addr: JsAddress): String = {
    val diff = addressStringLengthDiff(addr)
    val length = toStringLength + diff
    val sb = buildToString(new jl.StringBuilder(length + 12), length, diff, _.toStringWithAddress(addr))
    sb.toString
  }

  private def addressStringLengthDiff(addr: JsAddress): Int = {
    val r = root
    addr.toString.length - r.address.toString.length
  }

  /**
   * Optimized toString construction. Used by `toString`, `toSerializationFormat`,
   * and friends `WithAddress`
   * @param sb builder that will be modified (and same instance is returned)
   * @param length pre-calculated length of the to be constructed String, not
   *               necessarily same as sb.capacity because more things may be appended to the
   *               sb afterwards
   * @param diff difference in offset for each child element, due to different address
   * @param rootString function to construct the root element string
   */
  private def buildToString(sb: jl.StringBuilder, length: Int, diff: Int, rootString: JsRootActorPath ⇒ String): jl.StringBuilder = {
    @tailrec
    def rec(p: JsActorPath): jl.StringBuilder = p match {
      case r: JsRootActorPath ⇒
        val rootStr = rootString(r)
        sb.replace(0, rootStr.length, rootStr)
      case c: JsChildActorPath ⇒
        val start = c.toStringOffset + diff
        val end = start + c.name.length
        sb.replace(start, end, c.name)
        if (c ne this)
          sb.replace(end, end + 1, "/")
        rec(c.parent)
    }

    sb.setLength(length)
    rec(this)
  }

  override def equals(other: Any): Boolean = {
    @tailrec
    def rec(left: JsActorPath, right: JsActorPath): Boolean =
      if (left eq right) true
      else if (left.isInstanceOf[JsRootActorPath]) left equals right
      else if (right.isInstanceOf[JsRootActorPath]) right equals left
      else left.name == right.name && rec(left.parent, right.parent)

    other match {
      case p: JsActorPath ⇒ rec(this, p)
      case _ ⇒ false
    }
  }

  override def hashCode: Int = {
    toSerializationFormat.##
  }

  override def compareTo(other: JsActorPath): Int = {
    @tailrec
    def rec(left: JsActorPath, right: JsActorPath): Int =
      if (left eq right) 0
      else if (left.isInstanceOf[JsRootActorPath]) left compareTo right
      else if (right.isInstanceOf[JsRootActorPath]) -(right compareTo left)
      else {
        val x = left.name compareTo right.name
        if (x == 0) rec(left.parent, right.parent)
        else x
      }

    rec(this, other)
  }
}

