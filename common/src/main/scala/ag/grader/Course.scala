package ag.grader

class Course(
    val name: CourseId,
    projects_ : Map[String, ProjectData],
    val data: CourseData
)(using World) {

  val enrollmentId: String =
    s"${name}__enrollment"

  lazy val enrollmentDir: os.Path =
    Config.get().reposDir / enrollmentId

  lazy val students: Map[StudentId, StudentData] = (for {
    p <- os.list(enrollmentDir)
    if os.isFile(p)
    last = p.last
    if !last.startsWith(".")
  } yield {
    val studentId = StudentId(last)
    val studentName = os.read(p).trim().nn
    studentId -> StudentData(studentId, studentName)
  }).toMap

  lazy val projects: List[Project] = (for {
    (n, d) <- projects_
    if d.active
  } yield Project(n, this, d)).toList.sortBy(_.name)

  def send_to(student_id: StudentId): String = {
    val config = Config.get()
    val t = if (data.send_to_students) {
      student_id.toString
    } else {
      config.reportDefaultUser
    }
    s"$t@${config.reportDomain}"
  }

  def knownRepos(): LazyList[KnownRepo] = {

    val a = LazyList(EnrollmentRepo(this))

    val b = for {
      p <- projects
      r <- p.knownRepos
    } yield r

    a ++ b
  }

  override def hashCode: Int = name.hashCode
  override def equals(rhs: Any): Boolean = rhs match {
    case other: Course => name == other.name
    case _             => false
  }
  // override def toString: String =
  //  s"Course($name)"

}

object Course {

  given Ordering[Course] = Ordering.by(_.name)

}
