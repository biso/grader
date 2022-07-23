package ag.grader

import munit.FunSuite

class GitServiceTests extends FunSuite {
  given canThrowShellException: CanThrow[Exception] = compiletime.erasedValue
  test("t1") {
    val t = os.temp.dir(deleteOnExit = true)

    given world: World = World(
      classOf[Config] -> SimpleConfig(
        gitHost = "linux5",
        gitUser = "git",
        gitPort = 22,
        threads = 1,
        optBaseDir = Some(t),
        reportDomain = "example.com",
        reportDefaultUser = "bob"
      )
    )

    val config = Config.get()
    val git = GitService.get()

    // given git: GitService = SimpleGitService

    val d = config.reposDir / "grader"
    assert(clue(!os.isDir(d)))
    git.clone("grader")
    assert(clue(os.isDir(d)))
    assert(clue(os.isFile(d / "Makefile")))
    cd(d) {
      assert(clue(!git.rev_parse("HEAD").isEmpty))
      assert(clue(git.rev_parse("Should be never there").isEmpty))
    }
  }
}
