package ag.grader

import language.experimental.saferExceptions

def update_keys(allActiveCoursesSorted: Seq[Course])(using World): Unit throws ShellException = {
  val config = Config.get()
  allActiveCoursesSorted.foreach { course =>
    val courseName = course.name.id
    // We assume it exists
    val courseBox = config.dropBox / courseName

    val students = for {
      file <- os.list(courseBox)
      if file.last.endsWith(".pub")
      if os.isFile(file, followLinks = false)
    } yield check_key(course, file)

    // Cleanup from previous failed runs
    course.enrollmentDir.git_restore()

    val newly_enrolled = new_enrollments(course, students)

    if (newly_enrolled.nonEmpty) {
      // we have new enrollments

      // push changes
      cd(course.enrollmentDir) {
        sh("git", "push")
      }

      // send them e-mails
      newly_enrolled.foreach { studentId =>
        send_enrollment_email(course, studentId)
      }
    }
  }
}


