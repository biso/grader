package ag.grader

import language.experimental.saferExceptions

@main def run(args: String*): Unit = {

  if (args.nonEmpty) {
    sys.error("usage: ./run")
  }

  given World = World()
  given canThrowShellException: CanThrow[ShellException] =
    compiletime.erasedValue

  val config: Config = Config.get()

  import config._

  ///////////////
  // run tests //
  ///////////////

  for {
    dir <- os.list(preparedDir)
    if os.isDir(dir)
  } {
    val studentName = dir.last

    val outputFile = os.pwd / s"$studentName.output"

    println(dir)

    if (!os.exists(outputFile)) {
      os.write(outputFile, s"$studentName\n")
    }

    cd(dir) {
      try {
        sh("make", "clean")
        sh("make", "-s", "-k", "test")
      } catch {
        case e: ShellException =>
          System.err.nn.println(e)
      }
    }
    os.list(dir).filter(_.last.endsWith(".result")).foreach { path =>

      val testName = path.last.replaceFirst("""\.result$""", "")

      os.write.append(outputFile, s"$testName ${os.read(path).trim}\n")
    }

  }

}
