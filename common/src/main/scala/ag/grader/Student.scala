package ag.grader

import upickle.default._

case class Student(id: Name[Student], real_name: String)
    derives ReadWriter {}
