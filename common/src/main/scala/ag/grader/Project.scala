package ag.grader

import language.experimental.saferExceptions

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import scala.jdk.CollectionConverters._

import java.time.{Instant, ZoneId}
import java.security.MessageDigest

class Project(val name: String, val course: Course, val data: ProjectData) {

  def fullName: String = s"${course.name}_$name"

  override def equals(other: Any): Boolean = other match {
    case o: Project => fullName == o.fullName
    case _          => false
  }

  override def hashCode(): Int = {
    fullName.hashCode()
  }

  val chosenAliases: Set[String] = data.chosen.toSet

  // Entrolled students come from the course
  // all_students come from browsing the submissions + entrolled students
  // This way we can keep old tests, tests from students who later dropped, etc.

  lazy val submission_pattern = s"${fullName}_([^_]*)".r

  def all_students(using World): Seq[Name[StudentData]] = {
    os.list(Config.get().reposDir).flatMap { path =>
      path.last match {
        case submission_pattern(x) => Seq(Name[StudentData](x))
        case _                     => Seq()
      }
    }
  }

  def isBadTest(testName: String): Boolean = data.bad_tests.contains(testName)

  lazy val chosen_weights: Map[String, Double] = (for {
    test_name <- data.chosen
    w = data.weights.find(w => w.regex.matches(test_name)).get.weight
  } yield (test_name, w)).to(Map)

  def isChosen(fullTestName: String): Boolean = {
    val x = fullTestName.split('_')
    chosenAliases.contains(x(0))
  }

  def projectDir(using World): os.Path =
    Config.get().reposDir / fullName

  lazy val overrideId: String =
    s"${fullName}__override"

  def overrideDir(using World): os.Path =
    Config.get().reposDir / overrideId

  lazy val testsId: String =
    s"${fullName}__tests"

  def testsDir(using World): os.Path =
    Config.get().reposDir / testsId

  lazy val aliasId: String =
    s"${fullName}__aliases"

  def aliasDir(using World): os.Path =
    Config.get().reposDir / aliasId

  lazy val resultsId: String =
    s"${fullName}__results"

  def resultsDir(using World): os.Path =
    Config.get().reposDir / resultsId

  def submissionId(student: Name[StudentData]): String =
    s"${fullName}_$student"

  def submissionDir(student: Name[StudentData])(using World): os.Path =
    Config.get().reposDir / submissionId(student)

  def submissionResultsId(student: Name[StudentData]): String =
    s"${submissionId(student)}_results"

  def submissionResultsDir(student: Name[StudentData])(using World): os.Path =
    Config.get().reposDir / submissionResultsId(student)

  def scratchDir(student: Name[StudentData])(using World): os.Path =
    Config.get().scratchDir / s"${fullName}_$student"

  def removeScratchDir(csid: Name[StudentData])(using World): Unit = {
    os.remove.all(scratchDir(csid))
  }

  lazy val knownRepos: LazyList[KnownRepo] = {
    val a = LazyList(
      ProjectRepo(this),
      OverrideRepo(this),
      TestsRepo(this),
      ResultsRepo(this),
      AliasRepo(this)
    )

    val b = for {
      s <- LazyList.from(course.students.keys)
      r <- LazyList(StudentSubmissionRepo(this, s), StudentResultsRepo(this, s))
    } yield r

    a ++ b
  }

  def something_changed()(using World): Boolean = {
    knownRepos.exists(r =>
      !(Config.get().reposDir / r.repoId).hasTag("processed", "")
    )
  }

  def mark_processed()(using World): Unit = {
    knownRepos.foreach { r =>
      (Config.get().reposDir / r.repoId).setTag("processed", "")
    }
  }

  def ensureScratchDir(csid: Name[StudentData])(using World): os.Path = {
    val dir = scratchDir(csid)
    if (!os.isDir(dir)) {
      createScratchDir(csid)
    }
    dir
  }

  def createScratchDir(student: Name[StudentData])(using World): os.Path = {
    val scratch_dir = scratchDir(student)
    val submission_dir = submissionDir(student)

    // copy from submission
    submission_dir.copy(scratch_dir)
    // os.copy(from = submission_dir, to = scratch_dir, replaceExisting = false, followLinks = false, createFolders = true)

    // remove all tests
    os.list(scratch_dir).foreach { path =>
      if (data.test_extensions.contains(path.ext)) {
        os.remove.all(path)
      }
    }

    // copy tests
    os.list(testsDir).filterNot(_.last == ".git").foreach { path =>
      path.copy(scratch_dir / path.last)
    }

    // replace with override
    os.walk(
      overrideDir,
      includeTarget = false,
      skip = { path => path.last == ".git" }
    ).filterNot(path => os.isDir(path, followLinks = false))
      .map(_.relativeTo(overrideDir))
      .foreach { rp =>
        val dest = scratch_dir / rp
        val src = overrideDir / rp
        os.makeDir.all(dest / os.up)
        src.copy(dest)
      }

    // remove .git
    os.remove.all(scratch_dir / ".git")

    scratch_dir
  }

  /* get the student's alias for this project */
  def getStudentAlias(s: Name[StudentData])(using World): String throws
    ShellException =
    synchronized {
      if (os.exists(aliasDir / s.toString)) {
        os.read(aliasDir / s.toString).trim.nn
      } else {
        val newIndex = os.list(aliasDir).size
        val newAlias = f"$newIndex%03d"
        os.write(aliasDir / s.toString, newAlias)
        cd(aliasDir) {
          sh("git", "add", s.toString)
          sh("git", "commit", "-m", s"added alias for $s")
          sh("git", "push")
        }
        echo(s"$fullName:$s => $newAlias")
        newAlias
      }
    }

  def dockerCommand(workingDir: os.Path)(using World): Seq[String] = {
    data.docker_file.toSeq.flatMap { docker_file_name =>
      val config = Config.get()
      Seq(
        "docker",
        "run",
        "--rm",
        "-v",
        s"${workingDir.toString}:/work",
        "-w",
        "/work",
        "-u",
        s"${config.userId}:${config.groupId}",
        "-t",
        Docker.of(os.pwd / docker_file_name)
      )
    }
  }

  def copy_tests(
      students: Iterable[Name[StudentData]]
  )(using World): Unit throws ShellException = {
    val config = Config.get()
    def copy_one_test(test_name: String, paths: Seq[os.Path]) = {
      val md = MessageDigest.getInstance("MD5").nn
      for {
        p <- paths.sortBy(_.last)
      } {
        md.update(p)
      }
      val sig = md.digest().nn.toSeq.map(b => f"$b%02x").mkString
      val sig_file = testsDir / s"$test_name.sig"
      if ((!os.exists(sig_file) || (os.read(sig_file) != sig))) {
        for {
          p <- paths
        } {
          val dest = testsDir / s"$test_name.${p.ext}"
          os.remove.all(dest)
          p.copy(dest)
        }
        os.write.over(sig_file, sig)
      }
    }

    // val override_sha = overrideDir.git_sha()

    overrideDir.checkTag("copied_tests", "") {
      echo(s"    ${overrideDir.last} may have changed")
      // Copy the given tests
      overrideDir.get_all_tests(this).foreach { case (test_name, paths) =>
        copy_one_test(test_name, paths)
      }
      testsDir.gitAddCommitPush(s"updated tests from ${overrideDir.last}")
    }

    // copy student tests
    students.map { s =>
      val repoId = s"${fullName}_$s"
      val repoDir = config.reposDir / repoId

      repoDir.checkTag("copied_tests", "") {

        echo(s"    $repoId may have changed")

        // val studentScratchDir = config.scratchDir / repoId
        val tempDir =
          os.temp.dir(config.scratchDir, "tests", deleteOnExit = false)

        /* make a scratch clone */
        {
          import language.unsafeNulls
          Git
            .cloneRepository()
            .setBare(false)
            .setCloneAllBranches(true)
            .setDirectory(tempDir.toIO)
            .setURI(repoDir.toNIO.toAbsolutePath.toString)
            .call()
            .close()
        }

        tempDir.withGit { g =>
          // Looking for the most recent commit before the test deadline
          import language.unsafeNulls

          g.log()
            .call()
            .asScala
            .find { c =>
              val d = Instant
                .ofEpochMilli(1000L * c.getCommitTime)
                .atZone(ZoneId.systemDefault)
                .toLocalDateTime
              d.compareTo(data.test_cutoff) <= 0
            }
            .foreach { (c: RevCommit) =>
              // Found it
              cd(tempDir) {
                val sha = c.name()

                g.checkout().nn.setName(sha).nn.call()

                val test_paths =
                  tempDir.get_student_tests(s, this).sortBy(_.toString)

                // look for student created test files
                if (test_paths.length == data.test_extensions.length) {
                  // found student test files, let's compute a signature

                  val alias = getStudentAlias(s)
                  copy_one_test(alias, test_paths)
                }
              }
            }

          // os.remove.all(studentScratchDir)

        }

        testsDir.gitAddCommitPush(s"updated tests")
      }
    }
  }

}

object Project {

  given Ordering[Project] = Ordering.by(_.fullName)

}
