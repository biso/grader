package ag.grader

class DockerTests extends munit.FunSuite {

  test("t1".ignore) {
    val config = SimpleConfig(
      gitHost = "linux5",
      gitUser = "git",
      gitPort = 22,
      threads = 1,
      optBaseDir = Some(os.temp.dir(deleteOnExit = true)),
      reportDomain = "example.com",
      reportDefaultUser = "bob"
    )
    Given(World(classOf[Config] -> config)) {
      val img = Docker.of(os.pwd / os.up / "Dockerfile.cs429h_s22")
      // assert(clue(img) == clue(""))

      val x = os.proc("docker", "run", "-t", img, "ls", "-1").call().out.lines()

      for { d <- Set("tmp", "bin", "sbin", "home") } {
        assert(clue(x.contains(d)))
      }

    }

  }

}
