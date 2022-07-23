package ag.grader

import upickle.default._

case class StudentData(id: Name[StudentData], name: String)
    derives ReadWriter {}
