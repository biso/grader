package ag.grader

import language.experimental.saferExceptions

import scala.util.control.NonFatal

case class KnownRepoException(cause: Exception | Null) extends Exception(cause)

inline def wrap[T, A <: Exception, B <: Exception](f: => T throws A)(
    translate: A => B
): T throws B =
  try f
  catch
    case e: A =>
      throw translate(e)

sealed trait KnownRepo {
  def repoId: String
  def forkOf: KnownRepo | Null
  def readers: Seq[String]
  def writers: Seq[String]

  // populate the repo after it has been created
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException

  // do any post creation work. The repo is fully populated
  def postCreate(dir: os.Path)(using World): Unit

  // true => repo was created
  def check(createIt: Boolean)(using World): Unit throws KnownRepoException = {
    val config: Config = Config.get()
    val dir = config.reposDir / repoId
    if (!os.exists(dir)) {
      val gitService = GitService.get()
      // Repo is missing
      try {
        // try to clone it
        try gitService.clone(repoId)
        catch case ge: GitException => KnownRepoException(ge)
      } catch {
        case NonFatal(_) =>
          // Failed to clone, try to create it
          forkOf match {
            case r: KnownRepo if createIt =>
              // We can create it as a fork
              critical(s"forking $repoId from ${r.repoId}") {
                val gitolite = GitoliteService.get()
                // Fork

                try
                  gitolite.fork(r.repoId, repoId)
                  readers.foreach(gitolite.add_reader(repoId, _))
                  writers.foreach(gitolite.add_writer(repoId, _))

                  // Let's try to clone it again
                  gitService.clone(repoId)

                  // populate it
                  populate(dir)

                  // commit any changes

                  dir.gitAddCommitPush("initial contents")
                catch
                  case e: (ShellException | GitoliteException | GitException) =>
                    throw KnownRepoException(e)

                postCreate(dir)

              }
            case _ =>
              throw new RuntimeException(s"don't know how to make $repoId")
          }
      }
    }
  }
}

object GitoliteRepo extends KnownRepo {
  def repoId = "gitolite-admin"
  def forkOf = null
  def readers = Seq()
  def writers = Seq()
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException = ???
  def postCreate(dir: os.Path)(using World): Unit = ???
}

object EmptyRepo extends KnownRepo {
  def repoId = "empty"
  def forkOf = null
  def readers = Seq()
  def writers = Seq()
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException = ???
  def postCreate(dir: os.Path)(using World): Unit = ???
}

class EnrollmentRepo(course: Course) extends KnownRepo {
  def repoId = course.enrollmentId
  def forkOf: KnownRepo = EmptyRepo
  def readers = Seq()
  def writers = Seq()
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException = {}
  def postCreate(dir: os.Path)(using World): Unit = {}
}

class ProjectRepo(project: Project) extends KnownRepo {
  def repoId: String = project.fullName
  def forkOf: Null = null
  def readers: Seq[Nothing] = Seq()
  def writers: Seq[Nothing] = Seq()
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException = ???
  def postCreate(dir: os.Path)(using World): Unit = ???
}

class AliasRepo(project: Project) extends KnownRepo {
  def repoId: String = project.aliasId
  def forkOf: KnownRepo = EmptyRepo
  def readers: Seq[Nothing] = Seq()
  def writers: Seq[Nothing] = Seq()
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException = ()
  def postCreate(dir: os.Path)(using World): Unit = ()
}

class OverrideRepo(project: Project) extends KnownRepo {
  def repoId: String = project.overrideId
  def forkOf: EmptyRepo.type = EmptyRepo
  def readers: Seq[Nothing] = Seq()
  def writers: Seq[Nothing] = Seq()
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException = {
    given Config = Config.get()
    ProjectRepo(project).check(true) // TODO: potential circular dependency
    project.projectDir.get_all_tests(project).foreach { case (_, paths) =>
      paths.foreach { path =>
        val dest = dir / path.last
        if (!os.exists(dest)) {
          path.copy(dest)
          // os.copy(from = path, to = dest, followLinks = false)
        }
      }
    }

    if (os.exists(project.projectDir / "Makefile")) {
      os.copy(
        from = project.projectDir / "Makefile",
        to = dir / "Makefile"
      )
    } else {
      // no Makefile in project repo, creating fake one
      val out = dir / "Makefile"
      out < "%:\n"
      out << "\techo \"$@\""
    }
  }
  def postCreate(dir: os.Path)(using World): Unit = ()
}

class TestsRepo(project: Project) extends KnownRepo {
  def repoId = project.testsId
  def forkOf = EmptyRepo
  def readers = Seq("@all")
  def writers = Seq()
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException = ()
  def postCreate(dir: os.Path)(using World): Unit = ()
}

class ResultsRepo(project: Project) extends KnownRepo {
  def repoId = project.resultsId
  def forkOf = EmptyRepo
  def readers = Seq("@all")
  def writers = Seq()
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException = ()
  def postCreate(dir: os.Path)(using World): Unit = ()
}

class StudentSubmissionRepo(project: Project, student: StudentId)
    extends KnownRepo {
  def repoId = project.submissionId(student)
  def forkOf = ProjectRepo(project)
  def readers = Seq(student.toString)
  def writers = Seq(student.toString)
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException = ()
  def postCreate(dir: os.Path)(using World): Unit = {
    val config = Config.get()
    val url = s"${config.gitUser}@${config.gitHost}:$repoId"
    val subject = s"git clone $url"
    Sender
      .get()
      .send(
        project.course,
        "clone",
        student,
        subject,
        dir / "README"
      )
  }
}

class StudentResultsRepo(project: Project, student: StudentId)
    extends KnownRepo {
  def repoId = project.submissionResultsId(student)
  def forkOf = EmptyRepo
  def readers = Seq(student.toString)
  def writers = Seq()
  def populate(dir: os.Path)(using World): Unit throws KnownRepoException = ()
  def postCreate(dir: os.Path)(using World): Unit = ()
}
