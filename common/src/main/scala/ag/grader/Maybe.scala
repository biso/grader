package ag.grader

import compiletime.erasedValue
import upickle.default.*

/** Like Option but:
 *     - treats null as None
 *     - gets flattened when serialized
 *        Maybe(10)  => 10
 *        Maybe(null) => null
 *
 *        This implies loss of information; can't distinguish between
 *            Maybe(Maybe(null)) and Maybe(null)
 */

class Maybe[+A](val ma: A | Null) extends AnyVal derives CanEqual {

  inline def get(): A =
    ma.nn
  inline def isDefined(): Boolean =
    ma != null
  inline def isEmpty(): Boolean =
    ma == null
  inline def contains[B](b: B)(using CanEqual[A, B]): Boolean =
    (ma != null) && (ma == b)
  inline def map[B](f: A => B): Maybe[B] =
    Maybe(if (ma == null) null else f(ma))
  inline def flatMap[B](f: A => Maybe[B]): Maybe[B] =
    if (ma == null) Maybe(null) else f(ma)
  inline def filter(f: A => Boolean): Maybe[A] =
    if ((ma != null) && f(ma)) Maybe(ma) else Maybe(null)
  inline def getOrElse[B](d: => B): A | B =
    if (ma == null) d else ma

  transparent inline def to[F[+_]]: Any = inline erasedValue[F[A]] match {
    case _: List[A]   => if (ma == null) List() else List(ma)
    case _: Option[A] => (if (ma == null) None else Some(ma)): Option[A]
  }

  override def hashCode(): Int =
    if (ma == null) 0 else ma.hashCode()

  override def equals(other: Any): Boolean = other match {
    case rhs: Maybe[_] => ma == rhs.ma
    case _ => false
  }

}

object Maybe {
  given [A:ReadWriter]: ReadWriter[Maybe[A]] = readwriter[ujson.Value].bimap(
    ma =>
      val it = ma.ma
      if (it == null) ujson.Null else writeJs(it), 
    js =>
      val t = if (js.isNull) null else read[A](js)
      Maybe(t)
  )
}
