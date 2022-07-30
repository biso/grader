package ag.grader

import scala.collection.immutable.TreeMap
import scala.util.Try
import scala.math.Ordering.Implicits.given

def send_updates(allActiveCoursesSorted: Seq[Course])(using World): Unit = {
  val config = Config.get()
  for {
    c <- allActiveCoursesSorted
    p <- c.projects
    s <- c.students.values
      .filter(d => os.exists(p.submissionDir(d.id) / "spamon"))
      .toList
      .sortWith((l, r) => l.id < r.id)
  } {
    val givenTestNames = p.overrideDir.get_all_tests(p).keys.toSet

    val a_test_extension = p.data.test_extensions.headOption
    val resultsDir = p.submissionResultsDir(s.id)

    resultsDir.checkTag("report_sent", "") {

      if (os.exists(resultsDir / "spamon")) {

        val outcome: TreeMap[Name[Test], Result] = {
          val test_result_pairs = os.list(resultsDir)
            .filter(_.last.endsWith(".result"))
            .map(path =>
              Name[Test](path.last.replace(".result", "").nn) -> upickle.default.read[Result](os.read(path).trim().nn)
            )
          TreeMap(test_result_pairs: _*)
        }

        if (outcome.nonEmpty) {

          val alias = (resultsDir / "student_alias").read_string("?")
          val (given_tests, peer) = outcome.partition { case (k, _) =>
            givenTestNames.contains(k)
          }

          val nPassGiven = given_tests.count(kv => kv._2 == Result.PASS)
          val nGiven = given_tests.size

          val nPassPeer = peer.count(kv => kv._2 == Result.PASS)
          val nPeer = peer.size

          val hasReportFile = resultsDir / "has_report"

          val commitSha =
            Try(os.read(resultsDir / "commit_sha").trim.nn).toOption

          val hasTest = a_test_extension.forall(ext =>
            os.exists(p.testsDir / s"$alias.$ext")
          )

          val testMark = if (hasTest) "+" else "-"
          val reportMark = if (os.exists(hasReportFile)) "+" else "-"

          val subjectLine =
            s"[$nPassGiven/$nGiven:$nPassPeer/$nPeer:${testMark}T:${reportMark}R] ${p
              .submissionId(s.id)} [c:${commitSha.getOrElse("?").slice(0, 6)}]"

          val reportFile = os.temp(perms = os.PermSet.fromString("rw-------"))

          reportFile << s"""
                           |more information at: https://${config.reportDomain}/~${config.reportDefaultUser}/${p.fullName}.html
                           |your alias: $alias
                           |your details: git clone ${GitService.url(
            p.submissionResultsId(s.id)
          )}
                           |all tests: git clone ${GitService.url(p.testsId)}
                           |summary for project: git clone ${GitService.url(p.resultsId)}
                           |
                           |""".stripMargin

          commitSha.foreach { sha =>
            os.write.append(reportFile, s"code sha: $sha (git log $sha)\n")
          }
          os.write.append(reportFile, "\n")

          Try(os.read(resultsDir / "commit_msg").trim).foreach { msg =>
            os.write.append(reportFile, s"\n$msg\n\n")
          }

          outcome.foreach { case (t, r) =>
            os.write.append(reportFile, s"$t ... $r\n")
          }

          /** **************************************************************************************
            */

          echo(s"    $subjectLine")

          Sender.send(c, "results", s.id, subjectLine, reportFile)
        }
      }
    }
  }
}
