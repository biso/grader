package ag.grader

import compiletime.erasedValue

class Maybe[+A](ma: A | Null) extends AnyVal {

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

}
