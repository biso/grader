package ag.grader

import language.experimental.saferExceptions

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.revwalk.RevCommit

import scala.jdk.CollectionConverters._
import java.time.{Instant, LocalDateTime, ZoneId}
import scala.concurrent.duration._
import upickle.default._

import java.util.concurrent.atomic.AtomicInteger
import scala.util.{Failure, Success}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.immutable.TreeMap
import scala.collection.SortedSet

@main def main(raw_args: String*): Unit = {

  val args = Args().parse(raw_args.toSeq)
  import args._

  //given config: Config = SimpleConfig.load
  //given MyExecutionContext = MyExecutionContext()
  //given GitService = SimpleGitService()
  //given Sender = InvalidSender

  given World = World()
  given canThrowShellException: CanThrow[GitException|GitoliteException|KnownRepoException|ShellException] =
    compiletime.erasedValue

  val config: Config = Config.get()
  given ExecutionContext = MyExecutionContext.get()
  val git_service = GitService.get()

  import config._

  try {

    /////////////////////////
    // update remote repos //
    /////////////////////////

    updateRemoteRepos(s => should_consider(s))

    /////////////////////////////////////////////////
    // Make sure we have repos for active projects //
    /////////////////////////////////////////////////

    val activeCoursesSorted =
      Courses.load(cid => should_consider(cid.id)).toList.sortBy(_.name)

    // This will checkout all needed repos and fail
    // loudly of any is missing
    // TODO: more graceful failure handling
    activeCoursesSorted.foreach { c =>
      c.knownRepos().foreach { r =>
        r.check(createIt = false)
      }
    }

    // Repo cleanup
    // Go over some popular repos and make sure they're in a clean state
    for {
      c <- activeCoursesSorted
      p <- c.projects
      if p.data.active && should_consider(p)
    } {
      // tests
      val testsDir = p.testsDir
      testsDir.git_restore()
      for {
        bad_test <- p.data.bad_tests
      } {
        val prefix = s"${bad_test}."
        val bad_files = os.walk(testsDir, skip = !_.last.startsWith(prefix))
        if (bad_files.nonEmpty) {
          bad_files.foreach(f => os.remove.all(f))
          testsDir.gitAddCommitPush(s"removed $bad_test")
        }
      }

      // result
      val resultsDir = p.resultsDir
      resultsDir.git_restore()
    }

    // Assertion: all required repos exist and are up to date
    // not guaranteed: all are clean.

    echo(s"activeCourses: ${activeCoursesSorted.map(_.name.id)}")

    val activeProjects = for {
      c <- activeCoursesSorted
      p <- c.projects
      if p.data.active && should_consider(p)
      if args.forceFlag || p.something_changed()
    } yield p

    ///////////////////////////////////
    // find projects with activities //
    ///////////////////////////////////

    val projectsToConsider = {
      val ps: List[(Project, StudentId)] = for {
        p <- activeProjects
        projectDir = p.projectDir
        projectSha = projectDir.git_sha()
        s <- p.course.students.keys
        submissionDir = p.submissionDir(s)
        if submissionDir.git_sha() != projectSha // the student did something
      } yield (p, s)

      ps.groupMap(_._1)(_._2)
        .transform((_, ss) => ss.to(SortedSet))
        .to(TreeMap)
    }

    for (p <- projectsToConsider.keys) {
      echo(s"  ${p.name}")
    }

    ///////////////////////////////////////////////////////////////
    // Copy tests and figure out desired tag for active projects //
    ///////////////////////////////////////////////////////////////

    echo("copy student tests")

    projectsToConsider.foreach { (p, _) =>
      echo(s"  ${p.fullName}")

      // We copy tests from all students. This way we pick up
      // old tests, tests from students who dropped, etc.
      // Why? to handle the case were a test is chosen then the student drops the class

      // TODO: do better. For example, only copy chosen tests

      if (!p.data.ignore_tests) p.copy_tests(p.all_students)
    }

    /////////////////////////////
    // Find submissions to run //
    /////////////////////////////

    echo("figure out what to run")

    // This is rather complex. We'starting with a project->students mapping
    // and we want to decide what submissions need to run. The rules are:
    //   - if any test changed, run all submissions on that test.
    //     Why would a test change?
    //        * because a student submitted or changed a test before the deadline
    //        * because an instructor changed tests in the override directory
    //   - if a student made any code change, run their submission on all known tests
    //   - if the force (-f) flag was given, run evertthing
    // We end up with a list of test/submission pairs we need to run (ntr_list)
    val ntr_list: List[NeedToRun] = for {
      // Iterator over all project,students pairs that need to be considered
      (p, students) <- projectsToConsider.toList

      // should we ignore tests for this project?
      if !p.data.ignore_tests

      _ = echo(s"  ${p.fullName}")

      // now iterate over all students
      s <- students.toList.sorted

      // throw away submissions that don't need to be considered
      if should_consider(p, s)

      // of those that are left, iterate over all known tests
      (test_name, _) <- p.testsDir.get_all_tests(p)
      if !p.isBadTest(test_name)

      sig_file_name = s"$test_name.sig"
      sha_file_name = s"$test_name.student_sha"

      // the test signature
      test_sig_file = p.testsDir / sig_file_name
      test_sig = os.read(test_sig_file)

      s_dir = p.submissionDir(s)
      sr_dir = p.submissionResultsDir(s)
      sr_sig_file = sr_dir / sig_file_name
      sr_sig = if (os.exists(sr_sig_file)) os.read(sr_sig_file) else "?"
      sr_sha_file = sr_dir / sha_file_name
      sr_student_sha = if (os.exists(sr_sha_file)) os.read(sr_sha_file) else "*"
      student_sha = git_service.rev_parse(s_dir, "HEAD").get

      // we run this submission on this test if one of those 3 conditions is true:
      //    - we're forcing a run
      //    - the submission changed
      //    - the test changed
      if forceFlag || (sr_sig != test_sig) || (sr_student_sha != student_sha)
    } yield NeedToRun(
      project = p,
      csid = s,
      test_name = test_name,
      when_done = { () =>
        os.write.over(sr_sha_file, student_sha)
        os.copy(test_sig_file, sr_sig_file, replaceExisting = true)
      }
    )

    // Let's add some structure to this list of NeedToRun
    //   - turn it into a 2-level tree: Project => (csid => NeedToRun)
    //   - sort everything
    val ntr_tree: TreeMap[Project, TreeMap[StudentId, List[NeedToRun]]] =
      ntr_list
        .groupBy(_.project)
        .transform { (_, ntrs) =>
          ntrs
            .groupBy(_.csid)
            .transform { (_, ntrs) =>
              ntrs.sortBy(_.test_name)
            }
            .to(TreeMap)
        }
        .to(TreeMap)

    val to_run = ntr_list.size // How many runs do we need to do
    val togo_count =
      new AtomicInteger(to_run) // keep a count of how many we've run so far
    echo(
      s"  will run $to_run tests from ${ntr_tree.values.map(_.keySet.size).sum} submissions in ${ntr_tree.keySet.size} projects"
    )

    ///////////////
    // Run tests //
    ///////////////

    val results: List[(Project, os.Path)] =
      Await.result(
        Future.sequence {
          for {
            (p, student_test_map) <- ntr_tree.toList
            (studentId, the_tests) <- student_test_map.toList
          } yield {
            Future {
              run_one(p, studentId, the_tests, togo_count, to_run)
            }
          }
        },
        Duration(4, HOURS)
      )

    ///////////////////////////////
    // Commit changes to results //
    ///////////////////////////////

    echo("updating results")
    projectsToConsider.toList.sortBy(_._1.fullName).foreach {
      case (project, students) =>
        val resultsDir = project.resultsDir

        echo(s"  ${project.fullName}")

        resultsDir.git_restore()

        // iterator over all submissions with new results
        // Notice that we consider all submission results not just the one we ran.
        //    Why? Because we might have unreported results (e.g. someone terminated the runner early)
        val copied: SortedSet[os.Path] = for {
          studentId <- students
          submissionResultsDir = project.submissionResultsDir(studentId)
          // We tag the submission result repo with the "copied" tag to indicate
          // that this commit has been to copied.
          // We only do this after the summary results are safely pushed to the server
          // in order to handle failures and early termination
          if !submissionResultsDir.hasTag("copied", "")
        } yield {
          // The summary results directory contains one sub-directory per submissions
          // We only want to keep the latest results for each student so we use the
          // following nameing scheme for the subdirectories:
          //       <alias>_<submission_sha>
          //
          // This anonymizes the results and allows a student to verify the commit for
          // which the results were reported.
          //
          // It also allows the runner to easily delete the old results (<alias>_*)
          val alias = project.getStudentAlias(studentId)
          val prefix = s"${alias}_"

          // remove old results
          for {
            path <- os.list(resultsDir)
            if path.last.startsWith(prefix)
          } {
            os.remove.all(path)
          }

          val repoDir = project.submissionDir(studentId)

          val resultFiles =
            os.list(submissionResultsDir).filter(_.last.endsWith(".result"))
          val testResults = resultFiles
            .map(path =>
              path.segments.toList.last
                .dropRight(7) -> os.read(path).trim.nn
            )
            .toMap
          val outcome = OutcomeData(
            testResults,
            spamon = os.exists(repoDir / "spamon"),
            isLate = os.exists(submissionResultsDir / "is_late"),
            isMine = studentId.toString == config.reportDefaultUser
          )
          val mySha = repoDir.git_sha()

          val sub_path = resultsDir / s"$prefix$mySha"
          os.makeDir(sub_path)
          os.write(sub_path / "summary", write(outcome))
          submissionResultsDir
        }

        resultsDir.gitAddCommitPush("updated project results")

        // we made it past push, tag all the copied repos
        for (dir <- copied) dir.setTag("copied", "")

        // mark project as processed
        project.mark_processed()
    }

    trace(s"*** results: $results")
  } finally {
    trace("shutdown")
    summon[World].close()
  }

}
