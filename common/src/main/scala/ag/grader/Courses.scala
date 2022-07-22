package ag.grader

import upickle.default._

object Courses {

  def load(
      shouldUse: CourseId => Boolean
  )(using World): Iterable[Course] =
    for {
      (courseName, courseData) <- read[Map[String, CourseData]](
        (Config.get().baseDir / "courses.json").toIO
      )
      if courseData.active
      courseId = CourseId(courseName)
      if shouldUse(courseId)
    } yield Course(courseId, courseData.projects, courseData)

}
