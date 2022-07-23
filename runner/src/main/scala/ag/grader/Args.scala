package ag.grader

case class Args(things: Set[String] = Set(), forceFlag: Boolean = false) {

  def usage(): Nothing = {
    sys.error("""|usage: ... [-f] {<identifier>}
         |     -f -- force it to run")
         |     <identifier> -- name of course, project, or submission"
         |                     cs439h_f20 (course)
         |                     cs439h_f20_p1 (project)
         |                     cs439h_f20_p1_bob (submission)
         |""".stripMargin)
  }

  def parse(args: Seq[String]): Args = args match {
    case Seq() =>
      this
    case Seq("-f", rest @ _*) =>
      this.copy(forceFlag = true).parse(rest)
    case Seq(name, rest @ _*) =>
      this.copy(things = things + name).parse(rest)
    case _ =>
      usage()
  }

  def should_force(id: String): Boolean =
    forceFlag && things.exists(thing => id.startsWith(thing))

  def should_force(p: Project): Boolean =
    should_force(p.fullName)

  def should_force(p: Project, csid: Name[StudentData]): Boolean =
    should_force(p.submissionId(csid))

  def should_consider(id: String): Boolean =
    things.exists(thing => thing.startsWith(id) || id.startsWith(thing))

  def should_consider(c: Course): Boolean =
    should_consider(c.name.id)

  def should_consider(p: Project): Boolean =
    should_consider(p.fullName)

  def should_consider(p: Project, csid: Name[StudentData]): Boolean =
    should_consider(p.submissionId(csid))

}
