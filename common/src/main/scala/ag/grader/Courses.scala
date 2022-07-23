package ag.grader

import upickle.default._

object Courses {

  def load(
      shouldUse: Name[Course] => Boolean
  )(using World): Iterable[Course] =
    for {
      (courseName, courseData) <- read[Map[String, CourseData]](
        (Config.get().baseDir / "courses.json").toIO
      )
      if courseData.active
      courseId = Name[Course](courseName)
      if shouldUse(courseId)
    } yield Course(courseId, courseData.projects, courseData)

}
