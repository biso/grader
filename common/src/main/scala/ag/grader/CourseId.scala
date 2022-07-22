package ag.grader

import upickle.default._

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
