package ag.grader

/** Send enrollment confirmation e-mail */
def send_enrollment_email(course: Course, studentId: Name[Student])(using World): Unit = {
  val msg = os.temp(
    perms = os.PermSet.fromString("rw-------"),
    deleteOnExit = true,
    contents = s"""
                  |You will get per-project instructions as projects are released.
                  |For each project (let's call it "px"), you will interact with the
                  |following git repositories:
                  |   ${course.name.id}_px
                  |          the initial project code
                  |   ${course.name.id}_px_${studentId.id}
                  |          the place where you do your work (initially copied from ${course.name.id}_px)
                  |   ${course.name.id}_px_${studentId.id}_results
                  |          your results
                  |              initially empty. You can only read from it
                  |              gets populated/updated every time you submit your work or one of your peers updates
                  |                  their test (before the test deadline)
                  |              you can get to it using git commands (clone, pull, etc) or using "make get_my_results"
                  |   ${course.name.id}_px__results
                  |          contains the combined results (same information as the web site)
                  |          you probably don't need to look at it
                  |          some students don't like the web site and write programs that present the information in a better way
                  |          you can get to it using git commands or using "make get_summary"
                  |   ${course.name.id}_px__tests
                  |          containts all the tests
                  |          you can get it it using git command or using "make get_tests"
          """.stripMargin
  )

  Sender.send(
    course,
    "enrollment",
    studentId,
    s"${studentId.id} is participating in ${course.name.id}",
    msg
  )
}
