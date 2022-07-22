package ag.grader

import language.experimental.saferExceptions

import org.eclipse.jgit.api.ResetCommand
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.util.Calendar
import java.util.GregorianCalendar
import java.time.ZoneId

import collection.mutable

def updateRemoteRepos(should_consider: String => Boolean)(using World): Unit throws GitException | GitoliteException | ShellException = {
  val config: Config = Config.get()
  //import config._

  ///////////////////////////////////
  // What changed since last time? //
  ///////////////////////////////////

  echo(
    s"getting history from ${config.gitUser}@${config.gitHost}:${config.gitPort}"
  )

  //val (history_out, _) = cwd(os.pwd) { GitoliteService.get().history() }

  val latest = for {
    line <- GitoliteService.get().history()
    Array(id, sha) = line.split(' ')
    //_ = if (parts.length != 2) throw new Exception(line)
    //id = parts(0)
    //sha = parts(1)
    if sha != "HEAD"
    if (should_consider(id))
    dirName = config.reposDir / id
    exists = os.isDir(dirName, followLinks = true)
    if !exists || dirName.git_sha() != sha
  } {
    val gitService = GitService.get()
    if (exists) {
      echo(s"updating $id")
      //gitService.reset(dirName, ResetMode.HARD)
      cd(dirName) {
        sh("git", "reset", "--hard")
        sh("git", "clean", "-fdx")
        sh("git", "checkout", "master")
        sh("git", "pull")
      }
      
      if (dirName.git_sha() != sha) {
        echo(s"mismatch for $id: $sha != ${dirName.git_sha()}")
      }
    } else {
      gitService.clone(id)
    }
  }
}
