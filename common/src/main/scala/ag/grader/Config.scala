package ag.grader

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ExecutorService, Executors}
import upickle.default.*

import java.nio.file.NoSuchFileException
import java.security.MessageDigest
import scala.concurrent.ExecutionContext
import java.util.Properties
import java.time.OffsetDateTime
import scala.reflect.ClassTag

trait Config {

  // val executor: ExecutorService

  def counter: AtomicLong
  def criticalCount: AtomicLong
  val threadId: ThreadLocal[Long]

  val threads: Int

  // def gitPort: Int
  // def gitServer: SshServer

  def baseDir: os.Path
  def dropBox: os.Path
  def htmlDir: os.Path
  def preparedDir: os.Path
  def reposDir: os.Path
  def scratchDir: os.Path
  def stdOutErr: os.Path
  def traceFile: os.Path
  def workDir: os.Path

  def gitUser: String
  def gitHost: String
  def gitPort: Int
  def groupId: String
  def userId: String

  def reportDomain: String
  def reportDefaultUser: String
}

case class SimpleConfig(
    gitHost: String,
    gitUser: String,
    gitPort: Int,
    threads: Int,
    optBaseDir: Option[os.Path] = None,
    reportDomain: String,
    reportDefaultUser: String
) extends Config
    derives ReadWriter {
  val baseDir: os.Path = optBaseDir match {
    case Some(p) => p
    case _       => os.pwd
  }
  val workDir: os.Path = baseDir / "work"
  val traceFile: os.Path = baseDir / "trace"
  val gitLogDateFormat: String = "--date=format-local:%Y:%m:%d:%H:%M:%S"
  val dropBox: os.Path = os.home / "dropbox"
  val htmlDir: os.Path = os.home / "public_html"

  val userId: String = os.proc("id", "-u").call().out.lines().head
  val groupId: String = os.proc("id", "-g").call().out.lines().head

  // check critical failures from previous runs //
  {
    val criticalFiles = os.list(os.pwd).filter(_.last.endsWith(".critical"))
    if (criticalFiles.nonEmpty) {
      println(criticalFiles)
      throw new Exception(
        "critical files exists, handle the problem then run again. Just deleting the files is not a good idea"
      )
    }
  }

  val preparedDir: os.Path = baseDir / "prepared"

  val reposDir: os.Path = workDir / "repos"
  os.makeDir.all(reposDir)
  val stdOutErr: os.Path = workDir / "std"
  os.makeDir.all(stdOutErr)
  val scratchDir: os.Path = workDir / "scratch"
  os.makeDir.all(scratchDir)
  // val gitServer: SshServer = SshServer(gitUser, gitHost, gitPort)

  val counter: AtomicLong = new AtomicLong(0)

  val criticalCount: AtomicLong = new AtomicLong(0)

  private val threadIds = new AtomicLong(0)

  val threadId: ThreadLocal[Long] = new ThreadLocal[Long] {
    override def initialValue(): Long = threadIds.getAndIncrement()
  }

  os.remove.all(traceFile)
  os.write(traceFile, "")

  os.remove.all(stdOutErr)
  os.makeDir.all(stdOutErr)

  os.remove.all(scratchDir)
  os.makeDir.all(scratchDir)
}

object SimpleConfig {

  def load: SimpleConfig =
    read[SimpleConfig]((os.pwd / "config.json").toNIO)

}

object Config {
  def get()(using world: World): Config = world.get[Config] {
    SimpleConfig.load
  }
}
