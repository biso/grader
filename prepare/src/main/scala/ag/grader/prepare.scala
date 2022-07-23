package ag.grader

import language.experimental.saferExceptions

import java.time.LocalDateTime

import upickle.default._

import scala.util.control.NonFatal

import scala.math.Ordering.Implicits.given

@main def main(args: String*): Unit = {

  if (args.length != 2) {
    System.err.nn.println("usage: ./prepare <course> <project>")
    sys.exit(-1)
  }

  val courseName = args(0)
  val projectShortName = args(1)
  val projectName = s"${courseName}_$projectShortName"

  given canThrowShellException: CanThrow[
    GitException | GitoliteException | KnownRepoException | ShellException
  ] =
    compiletime.erasedValue

  given World = World()
  given config: Config = Config.get()
  given Sender = InvalidSender // make it part of config
  import config._

  // update remote repos
  echo("updating remote repos")
  updateRemoteRepos { s =>
    s.startsWith(projectName) || s.startsWith(s"${courseName}__")
  }

  os.remove.all(config.preparedDir)
  os.makeDir.all(config.preparedDir)

  echo(s"project:$projectName")

  ////////////
  // Course //
  ////////////

  val course = Courses.load(_.id == courseName).head

  ///////////////////
  // get all repos //
  ///////////////////

  course.knownRepos().foreach { r =>
    r.check(createIt = false)
  }

  ///////////////////////////////
  // Look for project to grade //
  ///////////////////////////////

  val project = course.projects
    .find(_.name == projectShortName)
    .getOrElse(
      throw new RuntimeException(s"$projectName is not a valid project")
    )

  /////////////
  // aliases //
  /////////////

  val aliasToStudentId = os
    .list(project.aliasDir)
    .filter(!_.last.startsWith("."))
    .map { path =>
      val studentId = Name[Student](path.last)
      val alias = os.read(path).trim.nn
      alias -> studentId
    }
    .toMap

  val studentIdToAlias: Map[Name[Student], String] =
    aliasToStudentId.map(_.swap)

  //////////////////////
  // commits to grade //
  //////////////////////

  val commitsFileName = s"$projectName.commits"
  val commitsFilePath = os.pwd / commitsFileName
  if (!os.exists(commitsFilePath)) {
    fatal(s"""$commitsFilePath is missing, please create and commit to git
           |    default is to use "master"
           |    add one line per exception
           |    each line should have the form: <csid> <sha>
           |""".stripMargin)
  }

  val studentIdToSha = os.read
    .lines(os.pwd / commitsFileName)
    .map(_.trim.nn)
    .filter(_.nonEmpty)
    .map(_.split(" +").nn.toList)
    .map { p =>
      if (p.length != 2) {
        throw new RuntimeException(
          s"bad line in $commitsFileName ${p.mkString(" ")}"
        )
      }
      p.map(_.nn)
    }
    .map(p => Name[Student](p(0).trim.nn) -> p(1).trim.nn)
    .toMap

  //////////////////////////
  // update student repos //
  //////////////////////////

  val students: Seq[Student] =
    project.course.students.values.toList.sortWith((l, r) => l.id < r.id)

  /////////////////////////////////////////////////////////////
  // create test directory and copy all student tests there  //
  /////////////////////////////////////////////////////////////

  val testsDir = scratchDir / "tests"
  os.makeDir.all(testsDir)

  echo(s"copying tests to $testsDir")
  students.map(_.id).foreach { studentId =>
    echo(s"  $studentId")
    studentIdToAlias.get(studentId) match {
      case Some(alias) if project.chosenAliases.contains(alias) =>
        project.data.test_extensions.foreach { ext =>
          val src = project.testsDir / s"$alias.$ext"
          val dest = testsDir / s"${studentId.id}.$ext"
          echo(s"        $src => $dest")
          os.copy(src, dest, followLinks = false)
        }
      case Some(alias) =>
        echo(s"    not using test ($alias)")
      case _ =>
        echo(s"    no alias")
    }

  }

  // Remove existing output files

  echo("removing *.output files")
  cd(os.pwd) {
    sh("rm", "-f", "*.output")
  }

  //////////////////////////////////////////////////////////
  // create student prepared directories and output files //
  //////////////////////////////////////////////////////////

  echo(s"creating prepared repos")
  students.foreach { case Student(studentId, studentName) =>
    val studentPreparedDir = preparedDir / studentId.toString

    echo(s" $studentPreparedDir")

    // clone from student repo
    cd(os.pwd) {
      sh("git", "clone", project.submissionDir(studentId), studentPreparedDir)
    }

    cd(studentPreparedDir) {
      val shaToUse = studentIdToSha.get(studentId).orElse {
        studentPreparedDir
          .git_history()
          .find { case (_, localDateTime) =>
            localDateTime.compareTo(project.data.code_cutoff) <= 0
          }
          .map(_._1)
      }
      shaToUse.foreach { sha =>
        sh("git", "checkout", sha)
      }
    }

    // remove all tests
    os.list(studentPreparedDir)
      .filter(path =>
        project.data.test_extensions.exists(ext =>
          path.last.endsWith("." + ext)
        )
      )
      .foreach(path => os.remove.all(path))

    // copy all tests over
    os.list(testsDir).filter(!_.last.startsWith(".")).foreach { path =>
      val dest = studentPreparedDir / path.last
      os.remove.all(dest)
      path.copy(dest)
    }

    // copy override directory over
    cd(os.pwd) {
      sh(
        "rsync",
        "-avp",
        "--exclude=.git/",
        project.overrideDir.toString + "/",
        studentPreparedDir.toString + "/"
      )
    }

    val outputFile = os.pwd / s"$studentId.output"
    os.write.over(outputFile, s"$studentName\n")

    val commitTime = studentPreparedDir.git_commit_time

    val isLate = commitTime.compareTo(project.data.code_cutoff) > 0

    os.write.append(outputFile, if (isLate) "late\n" else "onTime\n")
    os.write.append(
      outputFile,
      s"${studentPreparedDir.git_sha()} @ $commitTime\n"
    )

  }

  echo(s"""
         |Prepared submissions for ${project.fullName} are in $preparedDir
         |
         |For each student, you'll find a directory named after them that contains:
         |     - their most recent submission (unless the $projectName.commits file has a different sha)
         |     - All override files (e.g. Makefile) are replaced
         |     - All tests are replaced by the chosen tests
         |     - Peer tests are copied by name not alias
         |
         |For each student, you'll also find a file <csid>.output in the current directory
         |that contains information about their submission (full name, the commit id,
         |is their submission late, ...)
         |
         |At this point you can do a mix of 2 things:
         |    (1) run one round of tests (./run)
         |        Everytime you do that, a new round of results will be appended
         |        to the output files (*.output). You can write a simple script to
         |        tabulate this data to your liking, compute scores, etc.
         |
         |    (2) navigate to the prepared directory and run "make -s test" there or
         |        browse the code, etc.
         |
         |You can also navigate to ${reposDir.relativeTo(os.pwd)} to browse the
         |original student submissions, the tests directories, etc. Please don't
         |edit anything there; those repos should be treated as read-only
         |
         |It's a good idea to do long runs inside "screen" or "tmux".
         |
         |""".stripMargin)

}
