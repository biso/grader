package ag.grader

import scala.util.Try

extension [A](something: A) {
  def |>[B](f: A => B): B = f(something)
}

////extension [A](something: A | Null) {
//  def opt: Option[A] = if (something != null) Some(something) else None
//}
