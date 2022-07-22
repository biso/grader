package ag.grader

import language.experimental.saferExceptions

import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}
import scala.collection.immutable.TreeMap
import org.eclipse.jgit.api.errors.InvalidRemoteException
import scala.util.Using

import scala.math.Ordering.Implicits.given

// runs on a CS machine

@main def reporter(args: String*): Unit = {

  //val send_to: Option[String] = None

  //given World()

  val what = if (args.isEmpty) "" else args(0)

  def should_consider(name: String): Boolean = name.startsWith(what)

  Given(World()) {

    given canThrowShellException: CanThrow[GitException|GitoliteException|KnownRepoException|ShellException] =
      compiletime.erasedValue

    val config: Config = Config.get()
    val sender = Sender.get()

    import config._

    /////////////////////////
    // Update remote repos //
    /////////////////////////

    updateRemoteRepos(should_consider)

    //////////////////////////////////////
    // Load information for all courses //
    //////////////////////////////////////

    val allActiveCoursesSorted =
      Courses.load(c => should_consider(c.id)).toList.sortBy(_.name)

    /////////////////
    // Check repos //
    /////////////////

    echo("checking repos")

    GitoliteRepo.check(false)

    allActiveCoursesSorted.foreach { course =>
      // We must have the enrollment repo before we attempt to find all students
      EnrollmentRepo(course).check(true)
      course.knownRepos().foreach(_.check(true))
    }

    /////////////////////////////////////////////////
    // update keys in gitolite and enrollment repo //
    /////////////////////////////////////////////////

    val gitoliteDir = reposDir / "gitolite-admin"

    /*val courseToStudents1 =*/
    allActiveCoursesSorted.map { course =>
      val courseName = course.name.id
      // We assume it exists
      val courseBox = dropBox / courseName

      val students = os
        .list(courseBox)
        .filter(_.toString.endsWith(".pub"))
        .filter(p => os.isFile(p, followLinks = false))
        .map { keyPath =>
          val studentId =
            StudentId(keyPath.last.replaceFirst("""\..*""", "").nn)
          if (
            os.owner(keyPath, followLinks = false)
              .getName
              .nn != studentId.toString
          ) {
            throw new RuntimeException(s"$keyPath is owned by ${os.owner(keyPath)}")
          }

          val target = gitoliteDir / "keydir" / s"$studentId.pub"

          if (!os.exists(target) || os.read(target) != os.read(keyPath)) {

            cd(gitoliteDir) {
              sh("cp", keyPath, gitoliteDir / "keydir" / s"$studentId.pub")
              Try(sh("git", "add", "*")).map { _ =>
                sh("git", "commit", "-a", "-m", s"added $courseName:$studentId")
                sh("git", "push")
                echo(s"added $courseName:$studentId")
              }
            }

            val temp = os.temp()
            os.write.over(
              temp,
              s"To test connection: ssh -p ${config.gitPort} ${config.gitUser}@${config.gitHost} info"
            )

            Sender.send(
              course,
              "key",
              studentId,
              s"key updated for $studentId",
              temp
            )
          }

          studentId
        }

      // Cleanup from previous failed runs
      course.enrollmentDir.git_restore()

      val newly_enrolled = for {
        userId <- students
        file = course.enrollmentDir / userId.toString
        if !os.exists(file)
      } yield {
        import language.unsafeNulls
        val userName = os
          .read(cd(os.pwd) { sh("getent", "passwd", userId.toString)._1 })
          .trim
          .split(':')(4)
          .takeWhile(_ != ',')
          .trim

        echo(s"  enrolling $userId: $userName")

        os.write(file, userName)
        cd(course.enrollmentDir) {
          sh("git", "add", userId.toString)
          sh("git", "commit", "-m", userId.toString)
        }

        userId

      }

      if (newly_enrolled.nonEmpty) {
        // we have new enrollments

        // push changes
        cd(course.enrollmentDir) {
          sh("git", "push")
        }

        // send them e-mails
        newly_enrolled.foreach { studentId =>

          val msg = os.temp(
            deleteOnExit = true,
            contents = s"""
          |You will get per-project instructions as projects are released.
          |For each project (let's call it "px"), you will interact with the
          |following git repositories:
          |   ${course.name.id}_px                
          |          the initial project code
          |   ${course.name.id}_px_${studentId.id}
          |          the place where you do your work (initially copied from ${course.name.id}_px)
          |   ${course.name.id}_px_${studentId.id}_results
          |          your results
          |              initially empty. You can only read from it
          |              gets populated/updated every time you submit your work or one of your peers updates
          |                  their test (before the test deadline)
          |              you can get to it using git commands (clone, pull, etc) or using "make get_my_results"
          |   ${course.name.id}_px__results
          |          contains the combined results (same information as the web site)
          |          you probably don't need to look at it
          |          some students don't like the web site and write programs that present the information in a better way
          |          you can get to it using git commands or using "make get_summary"
          |   ${course.name.id}_px__tests
          |          containts all the tests
          |          you can get it it using git command or using "make get_tests"
          """.stripMargin
          )

          Sender.send(
            course,
            "enrollement",
            studentId,
            s"${studentId.id} is participating in ${course.name.id}",
            msg
          )
        }
      }

      (course.name, students)
    }.toMap

    //os.write.over(
    //  os.pwd / "enrollment.json",
    //  ujson.write(courseToStudents.transform((_,v) => v.map(_.toString)), indent = 4)
    //)

    //////////
    // HTML //
    //////////

    echo("generating HTML")

    allActiveCoursesSorted.foreach { course =>
      course.projects.foreach { project =>
        val resultsDir = project.resultsDir
        resultsDir.checkTag("report_tag", "") {



          // Get all the outcomes
          val outcomes = os
            .list(resultsDir)
            .filter(os.isDir)
            .filter(!_.last.startsWith("."))
            .map(p => p.last -> upickle.default.read[OutcomeData]((p / "summary").toNIO))
            .toMap

          // Split tests into chosen and non-chosen groups
          // We still display both
          val (chosenTestNames, otherTestNames) = outcomes.toSeq
            .flatMap(
              _._2.tests.keys.filterNot(testName => project.isBadTest(testName))
            )
            .sorted
            .distinct
            .groupBy(_.length)
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
          def testTitle(t: String, rowNum: Int)(using HtmlContext): Unit = {
            val ch = if (rowNum >= t.length) "." else t(rowNum)
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
              val short_name = s.take(8)
              val outcome = outcomes(s)

              val raw_score =
                project.chosen_weights.map { case (test_name, weight) =>
                  if (outcome.tests.getOrElse(test_name, "") == "pass") {
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
                  pre.title(s) {
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
                      outcome.tests.getOrElse(t, "") match {
                        case "pass" =>
                          ("color:green", ".")
                        case "fail" =>
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
                        text(test_name)
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
              //head(
              //    script(src = "https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js" )
              //),
              body {
                table {
                  tr(td(h1(text(project.fullName))))
                  tr(td(times))
                  tr(td(tbl))
                  tr(td(text("")))
                  tr(td(text(s"${course.students.keySet.size} enrollements")))
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
                //script.src("highlight_on_click.js")
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

    //////////////////////////
    // Send student updates //
    //////////////////////////

    echo("sending emails")

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

          val outcome = TreeMap(
            os.list(resultsDir)
              .filter(_.last.endsWith(".result"))
              .map(path =>
                path.last.replace(".result", "").nn -> os.read(path).trim().nn
              ): _*
          )

          if (outcome.nonEmpty) {

            val alias = (resultsDir / "student_alias").read_string("?")
            val (given_tests, peer) = outcome.partition { case (k, _) =>
              givenTestNames.contains(k)
            }

            val nPassGiven = given_tests.count(kv => kv._2 == "pass")
            val nGiven = given_tests.size

            val nPassPeer = peer.count(kv => kv._2 == "pass")
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

            val reportFile = os.temp()



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
    trace("shutdown")
  }

}
