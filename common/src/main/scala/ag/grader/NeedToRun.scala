package ag.grader

/** Infomation about tests that need to run */
case class NeedToRun(
    project: Project,
    csid: Name[Student],
    test_name: String,
    when_done: () => Any
)
