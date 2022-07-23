package ag.grader

/** Encapsulates the information we have about a course
  */
class Course(
    /** Its name */
    val name: Name[Course],

    /** mapping project names to raw project data (direct represntation from the
      * json file)
      */
    projects_ : Map[String, ProjectData],

    /** the course data from json */
    val data: CourseData
)(using World) {

  /** the identity of the corresponding enrollements repo
    */
  val enrollmentId: String =
    s"${name}__enrollment"

  /** a path to our local copy of the enrollement repo
    */
  lazy val enrollmentDir: os.Path =
    Config.get().reposDir / enrollmentId

  /** a mapping from student id to student data for all students participating
    * in this course
    */
  lazy val students: Map[Name[StudentData], StudentData] = (for {
    p <- os.list(enrollmentDir)
    if os.isFile(p)
    last = p.last
    if !last.startsWith(".")
  } yield {
    val studentId = Name[StudentData](last)
    val studentName = os.read(p).trim().nn
    studentId -> StudentData(studentId, studentName)
  }).toMap

  /** a list of all active projects */
  lazy val projects: List[Project] = (for {
    (n, d) <- projects_
    if d.active
  } yield Project(n, this, d)).toList.sortBy(_.name)

  /** Where should we send reports for the given student */
  def send_to(student_id: Name[StudentData]): String = {
    val config = Config.get()
    val t = if (data.send_to_students) {
      student_id.toString
    } else {
      config.reportDefaultUser
    }
    s"$t@${config.reportDomain}"
  }

  /* A list of all known repos for this course */
  def knownRepos(): LazyList[KnownRepo] = {

    // one enrollement repo per course
    val a = LazyList(EnrollmentRepo(this))

    // a collection of repos per project
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

}

object Course {

  /** default is to sort course by name */
  given Ordering[Course] = Ordering.by(_.name)

}
