package ag.grader

case class NeedToRun(
    project: Project,
    csid: Name[StudentData],
    test_name: String,
    when_done: () => Any
)
