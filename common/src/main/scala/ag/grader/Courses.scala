package ag.grader

import upickle.default._

object Courses {

  /** Load active courses with matching names
   *
   * @param shouldUse a function that returns True iff a course with a matching name should be loaded
   * @param World an implicit World instance
   */
  def load(
      shouldUse: Name[Course] => Boolean
  )(using World): Iterable[Course] =
    for {
      // All courses from the json file
      (courseName, courseData) <- read[Map[String, CourseData]](
        (Config.get().baseDir / "courses.json").toIO
      )
      
      // only consider active ones
      if courseData.active

      courseId = Name[Course](courseName)

      // only consider matching ones
      if shouldUse(courseId)
    } yield Course(courseId, courseData.projects, courseData)

}
