package ag.grader

import language.experimental.saferExceptions

import java.time.LocalDateTime
import upickle.default._
import java.util.concurrent.atomic.AtomicInteger
import java.time.ZoneId

// Run one test
def run_one(
    p: Project,
    studentId: StudentId,
    the_tests: List[NeedToRun],
    togo_count: AtomicInteger,
    to_run: Int
)(using World): (Project, os.Path) throws ShellException = {
  val config = Config.get()
  import config._

  // Those are the repos that particiapte in running
  val scratchDir = p.ensureScratchDir(studentId)
  val studentAlias = p.getStudentAlias(studentId)
  val repoId = p.submissionId(studentId)
  val repoDir = p.submissionDir(studentId)
  val submissionResultsDir = p.submissionResultsDir(studentId)

  echo(s"running $repoId")
  val submissionTime = repoDir.git_commit_time
  val isLate = submissionTime.compareTo(p.data.code_cutoff) > 0

  // submission results is the only directory that we modify in place
  // make sure it is prestine
  cd(submissionResultsDir) {
    sh("git", "clean", "-fdx")
    sh("git", "reset", "--hard")
  }

  val originalReport = reposDir / p.fullName / "REPORT.txt"
  val studentReport = scratchDir / "REPORT.txt"

  // see if the report has been updated
  val hasReport =
    os.exists(originalReport) && os.exists(studentReport) && (os.read(
      originalReport
    ) != os.read(studentReport))

  os.write.over(submissionResultsDir / "student_alias", studentAlias)

  os.write.over(
    submissionResultsDir / "commit_msg",
    os.read(cd(repoDir) { sh("git", "log", "-1", "--pretty=%s")._1 })
  )

  if (hasReport) {
    os.write.over(submissionResultsDir / "has_report", "")
  }

  if (isLate) {
    os.write.over(submissionResultsDir / "is_late", "")
  } else {
    os.remove.all(submissionResultsDir / "is_late")
  }

  val sha = p.submissionDir(studentId).git_sha()

  // Run the tests
  cd(scratchDir) {
    sh("make", "clean")

    the_tests.foreach { needToRun =>

      assert(p == needToRun.project)

      val testName = needToRun.test_name

      // run the test
      val resultFile = scratchDir / s"$testName.result"
      val timeFile = scratchDir / s"$testName.time"

      val cmd = p.dockerCommand(scratchDir) ++ Seq("make", s"$testName.result")

      val (runtime, stdout, stderr) = {
        val (t, (out, err)) = time {
          try {
            sh(cmd)
          } catch {
            case e: ShellException =>
              os.write.over(resultFile, "?")
              (e.out, e.err)
          }
        }
        if (!os.exists(timeFile)) {
          val centi_ = t / 10
          val seconds_ = centi_ / 100
          val centi = centi_ % 100
          val minutes = seconds_ / 60
          val seconds = seconds_ % 60
          os.write(timeFile, f"$minutes:$seconds%02d.$centi%02d")
        }
        echo(
          s"    [${togo_count.addAndGet(-1)}/$to_run]${
              if (p.data.docker_file.nonEmpty) "D"
              else ""
            } $repoId: $testName ${os.read(resultFile).trim} [${timeFile
              .read_string("?")}] $isLate"
        )
        (t, out, err)
      }

      val g1 = (Seq("result", "ok", "out", "raw", "time", "failure").map(n =>
        (scratchDir / s"$testName.$n", n)
      ))
      val g2 = Seq((stdout, "stdout"), (stderr, "stderr"))

      (g1 ++ g2).foreach { case (src, ext) =>
        val fn = s"${testName}.$ext"
        if (os.exists(src)) {
          os.write.over(submissionResultsDir / fn, os.read(src, count = 10000))
          // src.copy(submissionResultsDir / fn)
        }
      }

      needToRun.when_done()

    }
  }

  os.write.over(
    submissionResultsDir / "commit_sha",
    sha
  )

  if (os.exists(repoDir / "spamon")) {
    os.write.over(submissionResultsDir / "spamon", "")
  }

  submissionResultsDir.gitAddCommitPush("updated submission results")

  (p, scratchDir)

}
