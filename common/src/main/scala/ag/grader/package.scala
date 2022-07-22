package ag

import org.eclipse.jgit.api.errors.{
  EmptyCommitException,
  InvalidRemoteException
}
import org.eclipse.jgit.api.{Git, ResetCommand}
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.{SshSessionFactory, SshTransport}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*

import java.nio.file.{Files, LinkOption, StandardCopyOption}
import java.util
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal

import language.experimental.saferExceptions

package object grader {

  /* pickle nullable values */
  given [T:ReadWriter] : ReadWriter[T|Null] = readwriter[ujson.Value].bimap(
    v => if (v == null) ujson.Null else writeJs(v),
    js => if (js.isNull) null else read[T](js)
  )

  // Takes care of common shell boiler-plate: tracing, logging, error handling, etc
  def sh(parts: os.Shellable*)(using World, Cwd): (os.Path, os.Path) throws ShellException = {
    val config = Config.get()
    val c = config.counter.getAndIncrement()
    val out = config.stdOutErr / s"out$c"
    val err = config.stdOutErr / s"err$c"

    trace(s"[c$c] [${cwd()}] ${parts.flatMap(_.value).mkString(" ")}")
    val rc = os
      .proc(parts)
      .call(
        cwd = cwd(),
        stdout = out,
        stderr = err,
        check = false,
        timeout = 60 * 1000 * 1000
      )
    if (rc.exitCode != 0) {
      //config.say(s"    [c$c] failed with status ${rc.exitCode}")
      throw ShellException(c, parts.toList, out, err)
    }
    try {
      if (os.size(err) == 0) {
        os.remove(err)
      }
    } catch {
      case _ =>
    }
    (out, err)
  }

  // TODO: think again
  // We will use local time format. Git knows how to generate it, humans know how to type it,
  //     and Java knows how to parse it.

  // example: Mon Aug 12 09:10:55 2019
  val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy:MM:dd:HH:mm:ss").nn

  val displayFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").nn

  given ReadWriter[LocalDateTime] =
    readwriter[String].bimap[LocalDateTime](
      ins => ins.format(dateTimeFormatter).nn,
      str => LocalDateTime.parse(str, dateTimeFormatter).nn
    )

  given ReadWriter[os.Path] =
    readwriter[String].bimap[os.Path](
      p => p.toString,
      s => os.Path(s)
    )

  //////////////////
  // Shared logic //
  //////////////////

  // update remote repos

  def critical[A](s: String)(f: => A)(using World): A = {
    val config = Config.get()
    import config._

    val file = os.pwd / s
    criticalCount.incrementAndGet()

    echo(s"entered critical section: $s")
    os.write.over(file, "")
    try {
      f
    } finally {
      criticalCount.decrementAndGet()
      os.remove(file)
      echo(s"left critical section: $s")
    }

  }

  def trace(s: => String)(using World): Unit = synchronized {
    val config = Config.get()
    os.write.append(config.traceFile, s"[t${config.threadId.get}] $s\n")
  }

  def echo[A](s: => A)(using World): Unit = synchronized {
    val config = Config.get()
    //import config._
    val c = if (config.criticalCount.get != 0) "*" else " "
    println(s"$c[t${config.threadId.get}] $s")
  }

  def fatal[A](msg: => A)(using World): Nothing = {
    echo(msg)
    System.exit(1)
    ???
  }

  def time[A](f: => A): (Long, A) = {
    val start = System.currentTimeMillis()
    val out = f
    val delta = System.currentTimeMillis() - start
    (delta, out)
  }

}
