package ag.grader

import upickle.default._

case class Student(id: Name[Student], name: String)
    derives ReadWriter {}
