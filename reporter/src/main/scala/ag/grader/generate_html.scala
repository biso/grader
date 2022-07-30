package ag.grader

import language.experimental.saferExceptions

import java.time.LocalDateTime
import scala.util.Using

def generate_html(course: Course)(using World): Unit throws ShellException = {
  val config = Config.get()
  val htmlDir = config.htmlDir
  course.projects.foreach { project =>
    val resultsDir = project.resultsDir
    resultsDir.checkTag("report_tag", "") {

      // Get all the outcomes
      val outcomes: Map[Name[Test], OutcomeData] = os
        .list(resultsDir)
        .filter(os.isDir)
        .filter(!_.last.startsWith("."))
        .map(p =>
          Name[Test](p.last) -> upickle.default.read[OutcomeData]((p / "summary").toNIO)
        )
        .toMap

      // Split tests into chosen and non-chosen groups
      // We still display both
      val (chosenTestNames, otherTestNames) = outcomes.toSeq
        .flatMap(
          _._2.tests.keys.filterNot(testName => project.isBadTest(testName))
        )
        .sorted
        .distinct
        .groupBy(_.id.length)
        .toSeq
        .map { case (len, names) => (len, names.sorted) }
        .sortBy(_._1)
        .flatMap(_._2)
        .partition(name => project.isChosen(name))

      // compute the sum of all chosen weights
      val total = project.chosen_weights.values.sum

      // All the test name properly ordered
      // <chosen sorted by name> ++ <non-chosen sorte by name>
      val testNames = chosenTestNames ++ otherTestNames

      // sort submissions by submission id (<alias>_<sha>)
      val submissions = outcomes.keys.toSeq.sorted

      // find test extensions
      val testExtensions = project.data.test_extensions.toList.sorted

      // The table headers contains links to tests
      // This function generates one of those rows
      def testTitle(t: Name[Test], rowNum: Int)(using HtmlContext): Unit = {
        val ch = if (rowNum >= t.id.length) "." else t.id(rowNum)
        val ext =
          if (rowNum >= testExtensions.size) testExtensions.last
          else testExtensions(rowNum)
        val fileName = s"$t.$ext"

        td {
          pre {
            a.href(s"${project.testsId}/$t.$ext").title(fileName) {
              text(ch.toString)
            }
          }
        }
      }

      // Display the different time stamps
      def times(using HtmlContext): Unit = table {
        tr {
          td { text("generated") }
          td { text(displayFormat.format(LocalDateTime.now().nn).nn) }
        }
        tr {
          td { text("test cutoff") }
          td { text(displayFormat.format(project.data.test_cutoff).nn) }
        }
        tr {
          td { text("code cutoff") }
          td { text(displayFormat.format(project.data.code_cutoff).nn) }
        }
      }

      // Display the results table
      def tbl(using HtmlContext): Unit = table {
        /* 3 headers */
        for (i <- 0 to 2) {
          tr {
            td { text("") }
            td { text("") }
            testNames.foreach { t =>
              testTitle(t, i)
            }
          }
        }

        /* the actual results, one row per submission */
        submissions.foreach { s =>
          val short_name = s.id.take(8)
          val outcome: OutcomeData = outcomes(s)

          val raw_score =
            project.chosen_weights.map { case (test_name, weight) =>
              if (outcome.tests.get(test_name).contains(Result.PASS)) {
                if (outcome.isLate) {
                  weight / 2
                } else {
                  weight
                }
              } else {
                0.0
              }
            }.sum

          val score = ((raw_score * 1000.0) / total).toInt

          tr.bgcolor(if (outcome.isMine) "yellow" else null) {
            td {
              pre.title(s.id) {
                if (outcome.isLate) text(s"$short_name*")
                else text(short_name)
              }
            }
            td {
              pre.textAlign("right") {
                text(score.toString)
              }
            }
            testNames.foreach { t =>
              td.bgcolor(if (project.isChosen(t)) "LightGray" else null) {
                val (the_style, the_text) =
                  outcome.tests.get(t) match {
                    case Some(Result.PASS) =>
                      ("color:green", ".")
                    case Some(Result.FAIL) =>
                      ("color:red", "X")
                    case _ =>
                      (null, " ")
                  }
                pre.textAlign("center").style(the_style) {
                  text(the_text)
                }
              }
            }
          }
        }
      }

      def weights(using HtmlContext): Unit = table {
        tr {
          td {
            h3 {
              pre {
                text("Weights")
              }
            }
          }
        }
        tr {
          td {
            table {
              chosenTestNames.foreach { test_name =>
                tr {
                  td {
                    text(test_name.id)
                  }
                  td {
                    text(project.chosen_weights(test_name).toString)
                  }
                }
              }
            }
          }
        }
      }

      def ignored(using HtmlContext): Unit = table {
        tr { td { h3 { pre { text("Ignored tests") } } } }
        tr {
          td {
            table {
              project.data.bad_tests.toList.sorted.foreach { test_name =>
                tr {
                  td {
                    pre {
                      text(test_name)
                    }
                  }
                }
              }
            }
          }
        }
      }

      val f = htmlDir / s"${project.fullName}.html"
      val perms = os.PermSet.fromString("rwxr--r--")

      echo(s"  $f")

      os.write.over(f, "")
      os.perms.set(f, perms)

      Using(FileContext(f)) { file =>
        /*val h =*/
        html(file) {
          // head(
          //    script(src = "https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js" )
          // ),
          body {
            table {
              tr(td(h1(text(project.fullName))))
              tr(td(times))
              tr(td(tbl))
              tr(td(text("")))
              tr(td(text(s"${course.students.keySet.size} enrollments")))
              tr(td(text(s"${submissions.size} submissions")))
              tr(td(text(s"${testNames.size} tests")))
              tr {
                td.colspan("3") {
                  weights
                }
              }
              tr {
                td.colspan("3") {
                  ignored
                }
              }
            }
            // script.src("highlight_on_click.js")
          }
        }

      }

      ////////////////
      // Copy tests //
      ////////////////

      if (!project.data.ignore_tests) {
        os.remove.all(htmlDir / project.testsId)

        cd(htmlDir) {
          sh("git", "clone", project.testsDir, project.testsId)
          sh("chmod", "-R", "go+rx", project.testsId)
        }

        os.remove.all(htmlDir / project.testsId / ".git")
      }

    }

  }
}
