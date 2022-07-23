package ag.grader

import language.experimental.saferExceptions

case class GitException(cause: Exception) extends Exception(cause) {}

enum ResetMode {
  case SOFT, MIXED, HARD
}

trait GitService {

  def clone(repoName: String)(using World): os.Path throws GitException

  def reset(dir: os.Path, mode: ResetMode)(using World): Unit throws
    GitException

  def rev_parse(dir: os.Path, label: String | Null): Option[String]
  def rev_parse(label: String | Null)(using Cwd): Option[String] = {
    rev_parse(cwd(), label)
  }

}

object GitService {

  def url(user: String, host: String, port: Int | Null, name: String): String =
    port match {
      case p: Int => s"ssh://$user@$host:$p/$name"
      case _      => s"$user@$host:$name"
    }

  def url(name: String)(using World): String = {
    val config = Config.get()
    url(config.gitUser, config.gitHost, config.gitPort, name)
  }

  def get()(using world: World): GitService =
    world.get[GitService](SimpleGitService)

}

object SimpleGitService extends GitService {
  def clone(repoName: String)(using World): os.Path throws GitException = {
    val config = Config.get()
    val dir = config.reposDir
    os.makeDir.all(dir)
    cd(dir) {
      val out = dir / repoName
      echo(s"cloning to $out")
      try {
        sh(
          "git",
          "clone",
          GitService.url(
            config.gitUser,
            config.gitHost,
            config.gitPort,
            repoName
          )
        )
      } catch {
        case e: ShellException => throw GitException(e)
      }
      out
    }
  }

  def reset(dir: os.Path, mode: ResetMode)(using World): Unit throws
    GitException = {
    cd(dir) {
      try
        sh(
          "git",
          "reset",
          mode match
            case ResetMode.SOFT  => "--soft"
            case ResetMode.MIXED => "--mixed"
            case ResetMode.HARD  => "--hard"
        )
      catch case e: ShellException => throw GitException(e)
    }
  }

  def rev_parse(dir: os.Path, label: String | Null): Option[String] = {
    val the_label = label match {
      case s: String => s
      case _         => "HEAD"
    }
    val p = os
      .proc("git", "rev-parse", "-q", "--verify", the_label)
      .call(cwd = dir, check = false)
    if (p.exitCode == 0) {
      Some(p.out.text().trim.nn)
    } else {
      None
    }
  }
}
