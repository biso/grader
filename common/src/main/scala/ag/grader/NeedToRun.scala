package ag.grader

case class NeedToRun(
    project: Project,
    csid: StudentId,
    test_name: String,
    when_done: () => Any
)
