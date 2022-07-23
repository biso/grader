package ag.grader

import language.experimental.saferExceptions

case class GitoliteException(cause: Exception | Null) extends Exception(cause)

trait GitoliteService {

  def run(parts: os.Shellable*)(using World): (os.Path, os.Path) throws
    GitoliteException

  def fork(from: String, to: String)(using World): (os.Path, os.Path) throws
    GitoliteException = {
    run("fork", from, to)
  }

  def history()(using World): Seq[String] throws GitoliteException = {
    val (out, _) = run("history")
    os.read.lines(out)
  }

  def info()(using World): Seq[String] throws GitoliteException = {
    val (out, _) = run("info")
    os.read.lines(out)
  }

  def add(repoId: String, perm: String, who: String)(using
      World
  ): (os.Path, os.Path) throws GitoliteException = {
    run(
      "perms",
      repoId,
      "+",
      perm,
      who
    )
  }

  def add_reader(repoId: String, who: String)(using
      World
  ): (os.Path, os.Path) throws GitoliteException = {
    add(repoId, "READERS", who)
  }

  def add_writer(repoId: String, who: String)(using
      World
  ): (os.Path, os.Path) throws GitoliteException = {
    add(repoId, "WRITERS", who)
  }
}

object GitoliteService {
  def get()(using world: World): GitoliteService = world.get[GitoliteService] {
    SimpleGitoliteService()
  }
}

class SimpleGitoliteService extends GitoliteService {
  override def run(parts: os.Shellable*)(using World): (os.Path, os.Path) throws
    GitoliteException = {
    val config = Config.get()
    val sshService = SshService.get()
    try
      sshService.run_as(
        user = Maybe(config.gitUser),
        host = Maybe(config.gitHost),
        port = Maybe(config.gitPort),
        extra_flags = Seq("-T")
      )(parts: _*)
    catch case e: SshException => throw GitoliteException(e)
  }
}
