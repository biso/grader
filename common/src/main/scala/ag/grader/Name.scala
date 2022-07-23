package ag.grader

import upickle.default._

case class Name[T](id: String) derives CanEqual {
  override def toString: String = id
}

object Name {
  given [T]: ReadWriter[Name[T]] = readwriter[String].bimap[Name[T]](
    cid => cid.id,
    s => Name[T](s)
  )

  given [T]: Ordering[Name[T]] = Ordering.by(_.id)
}

/** simple wrapper around course name. This way we don't accidentally pass a
  * project name where a course name is expected
  */
/*
case class CourseId(id: String) derives CanEqual {
  override def toString: String = id
}

object CourseId {
  given ReadWriter[CourseId] = readwriter[String].bimap[CourseId](
    cid => cid.id,
    s => CourseId(s)
  )
  given Ordering[CourseId] = Ordering.by(_.id)
}
 */
