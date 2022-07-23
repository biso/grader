package ag.grader

import language.experimental.saferExceptions

class SshException(
    user: Maybe[String],
    host: Maybe[String],
    port: Maybe[Int],
    extra_flags: Seq[String],
    cause: Exception
) extends Exception(cause) {}

trait SshService {
  def run_as(
      user: Maybe[String] = Maybe(null),
      host: Maybe[String] = Maybe(null),
      port: Maybe[Int] = Maybe(null),
      extra_flags: Seq[String] = Seq()
  )(parts: os.Shellable*)(using World): (os.Path, os.Path) throws SshException
  def run(parts: os.Shellable*)(using World): (os.Path, os.Path) throws
    SshException = {
    run_as()(parts: _*)
  }
}

object SshService {
  def get()(using world: World): SshService = world.get[SshService] {
    SimpleSshService()
  }
  def run(parts: os.Shellable*)(using World): (os.Path, os.Path) throws
    SshException = {
    SshService.get().run(parts: _*)
  }
}

class SimpleSshService extends SshService {
  override def run_as(
      user: Maybe[String] = Maybe(null),
      host: Maybe[String] = Maybe(null),
      port: Maybe[Int] = Maybe(null),
      extra_flags: Seq[String] = Seq()
  )(parts: os.Shellable*)(using World): (os.Path, os.Path) throws
    SshException = {

    cd(os.pwd) {
      try {
        sh(
          "ssh",
          port.map(p => Seq("-p", p.toString)).getOrElse(Seq[String]()),
          extra_flags,
          s"${user.map(u => s"$u@").getOrElse("")}${host.getOrElse("localhost")}",
          parts
        )
      } catch {
        case e: ShellException =>
          throw SshException(user, host, port, extra_flags, e)
      }
    }
  }
}
