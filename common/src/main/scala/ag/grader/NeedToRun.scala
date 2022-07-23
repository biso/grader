package ag.grader

case class NeedToRun(
    project: Project,
    csid: Name[Student],
    test_name: String,
    when_done: () => Any
)
