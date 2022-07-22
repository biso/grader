package ag.grader

import munit.FunSuite

class GitoliteTests extends FunSuite {

  given canThrowShellException: CanThrow[Exception] = compiletime.erasedValue

  test("t1") {

    given world: World = World(
      classOf[Config] -> SimpleConfig(
        gitHost = "linux5",
        gitUser = "git",
        gitPort = 22,
        threads = 1,
        optBaseDir = Some(os.temp.dir(deleteOnExit = true)),
        reportDomain = "example.com",
        reportDefaultUser = "bob"
      )
    )

    val s = GitoliteService.get()

    //val (out,_) = s.info()
    //val lines = os.read.lines(out)
    //println(lines.mkString("\n"))
    List("empty", "grader", "gitolite-admin", "testing").foreach { n =>
      assert(clue(s.info().exists(_.contains(n))))
    }

    List("gitolite-admin", "grader").foreach { n =>
      assert(clue(s.history().exists(_.contains(n))))
    }

    //println(lines.filter(_.contains("empty")))

    //assert(clue(os.read.lines(out)) == Seq())

  }

}
