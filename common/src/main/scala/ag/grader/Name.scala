package ag.grader

import upickle.default._

/** A type-safe wrapper for names.
 * Things like courses, projects, and students all have string
 * names and one could accidentally pass a student name to something
 * that is expecting a project name, for example.
 * 
 * This class provides a type-safe wrappers for names. For example, a function
 * that expects a student name would have a signature like:
 *
 *      def f1(student_name: Name[Student])
 * 
 * instead of
 * 
 *      def f1(student_name: String)
 * 
 * Notice that using an opaque alias is more efficient than wrapping a string
 * in a case class but those don't work well with type-classes (ReadWriter, Ordering, ...)
 */
case class Name[T](id: String) derives CanEqual {
  override def toString: String = id
}

object Name {
  given [T]: ReadWriter[Name[T]] = readwriter[String].bimap[Name[T]](
    cid => cid.id,
    s => Name[T](s)
  )

  inline def self[A](using nm: sourcecode.Name): Name[A] =
    Name[A](nm.value)

  given [T]: Ordering[Name[T]] = Ordering.by(_.id)
}
