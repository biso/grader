package ag.grader

import upickle.default._

case class StudentId(id: String) derives CanEqual {
  override def toString: String = id
}

object StudentId {
  given ReadWriter[StudentId] = readwriter[String].bimap[StudentId](
    _.id,
    str => StudentId(str)
  )
  given Ordering[StudentId] = Ordering.by(_.id)
}

case class StudentData(id: StudentId, name: String) derives ReadWriter {}
