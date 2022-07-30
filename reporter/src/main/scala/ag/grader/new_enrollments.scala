package ag.grader

import language.experimental.saferExceptions

def new_enrollments(course: Course, students: Seq[Name[Student]])(using World): Seq[Name[Student]] throws ShellException = {
  for {
    userId <- students
    file = course.enrollmentDir / userId.toString
    if !os.exists(file)
  } yield {
    import language.unsafeNulls
    val userName = os
      .read(cd(os.pwd) { sh("getent", "passwd", userId.toString)._1 })
      .trim
      .split(':')(4)
      .takeWhile(_ != ',')
      .trim

    echo(s"  enrolling $userId: $userName")

    os.write(file, userName)
    cd(course.enrollmentDir) {
      sh("git", "add", userId.toString)
      sh("git", "commit", "-m", userId.toString)
    }

    userId

  }
}
