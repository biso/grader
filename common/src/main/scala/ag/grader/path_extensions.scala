package ag.grader

import language.experimental.saferExceptions

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.InvalidRemoteException
import java.time.LocalDateTime
import upickle.default._
import org.eclipse.jgit.api.errors.EmptyCommitException
import java.time.Instant

extension (file: os.Path) {

  def <(s: String): os.Path = {
    os.write(file, s)
    file
  }

  def <<(s: String): os.Path = {
    os.write.append(file, s)
    file
  }

  def copy(to: os.Path): Unit = {

    require(
      !to.startsWith(file),
      s"Can't copy a directory into itself: $to is inside $file"
    )

    def copyOne(p: os.Path) = {
      Files.copy(
        p.wrapped,
        (to / (p relativeTo file)).wrapped,
        StandardCopyOption.REPLACE_EXISTING,
        LinkOption.NOFOLLOW_LINKS
      )
    }

    copyOne(file)
    if (os.isDir(file, followLinks = false)) os.walk(file).map(copyOne)
  }

  def read_string(d: String): String =
    if (os.exists(file)) {
      os.read(file).trim.nn
    } else {
      d
    }

  def getFirstLine: String | Null =
    if (os.exists(file)) {
      os.read.lines(file).headOption.getOrElse(null)
    } else {
      null
    }

  def withRepo[A](f: Repository => A): A = {
    val repo =
      new FileRepositoryBuilder().setGitDir((file / ".git").toIO).nn.build().nn
    try {
      f(repo)
    } finally {
      repo.close()
    }
  }

  def withGit[A](f: Git => A): A = {
    withRepo { repo =>
      val g = new Git(repo)
      try {
        f(g)
      } finally {
        g.close()
      }
    }
  }

  def git_restore()(using World): Unit = if (os.isDir(file)) {
    file.withGit { g =>
      import language.unsafeNulls
      if (!g.status().nn.call().nn.isClean) {
        echo(s"  cleaning $file")
        g.clean()
          .setCleanDirectories(true)
          .setForce(true)
          .setIgnore(false)
          .call()
        g.reset().setMode(ResetCommand.ResetType.HARD).call()
      }
    }
  }

  def git_history(): Seq[(String, LocalDateTime)] = {
    withGit { g =>
      import scala.jdk.CollectionConverters._
      g.log()
        .nn
        .call()
        .nn
        .asScala
        .toSeq
        .map(c => (c.name.nn, c.getLocalCommitTime))
    }
  }

  def git_sha(): String = {
    withRepo { r =>
      val head = r.resolve("HEAD").nn
      head.toObjectId.nn.getName.nn
    }
  }

  def git_commit_time: LocalDateTime = {
    withGit { g =>
      import scala.jdk.CollectionConverters._
      g.log.nn.setMaxCount(1).nn.call().nn.asScala.head.getLocalCommitTime
    }
  }

  def get_student_tests(csid: StudentId, p: Project): Seq[os.Path] = {
    val paths = p.data.test_extensions.map(ext => file / s"$csid.$ext")
    if (paths.forall(os.exists)) paths else Seq()
  }

  def get_all_tests(p: Project): Map[String, Seq[os.Path]] = {
    os.list(file)
      . // all directory entries
      filter(path => p.data.test_extensions.contains(path.ext))
      . // keep the ones with the correct extensions
      groupMap(path => path.baseName)(x => x)
      . // group by base name
      filter { case (_, paths) =>
        paths.length == p.data.test_extensions.length
      } // keep the ones with the correct length
  }

  def gitAddCommitPush[A](msg: => String)(using World): Boolean throws
    ShellException = {
    cd(file) {
      sh("git", "add", "--all")
      val changed_files = os.read.lines(sh("git", "status", "--porcelain")._1)
      val changed = changed_files.nonEmpty
      if (changed) {
        sh("git", "commit", "-a", "-m", msg)
        echo(s"pushing ${file.last}")
        sh("git", "push")
      }
      changed
    }
  }

  def hasTag(tag: String, modifier: String)(using World): Boolean = {
    if (os.exists(file)) {
      val config = Config.get()
      val tagFile = config.workDir / "tags" / file.last / tag
      val mySha = file.git_sha()

      if (os.exists(tagFile)) {
        val (the_sha, the_modifier) = os.read.lines(tagFile) match {
          case Seq(the_sha)               => (the_sha, "")
          case Seq(the_sha, the_modifier) => (the_sha, the_modifier)
          case Seq(the_sha, the_modifier, _*) =>
            echo(s"extra lines in $tagFile, ignoring")
            (the_sha, the_modifier)
        }
        ((the_sha == mySha) && (the_modifier == modifier))
      } else {
        false
      }
    } else {
      false
    }
  }

  def setTag(tag: String, modifier: String)(using World) = {
    if (os.exists(file)) {
      val config = Config.get()
      val tagFile = config.workDir / "tags" / file.last / tag
      val mySha = file.git_sha()
      val text = s"$mySha\n$modifier"
      os.write.over(tagFile, text, createFolders = true)
    }
  }

  def checkTag[A](tag: String, modifier: String)(
      f: => A
  )(using World): Option[A] = {
    if (!file.hasTag(tag, modifier)) {
      val out = f
      file.setTag(tag, modifier)
      Some(out)
    } else {
      None
    }
  }

}
