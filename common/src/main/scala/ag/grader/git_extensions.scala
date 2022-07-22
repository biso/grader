package ag.grader

import org.eclipse.jgit.api.{Git, TransportConfigCallback}
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.SshSessionFactory

import scala.language.unsafeNulls
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.Transport
import java.time.LocalDateTime
import java.time.Instant
import org.eclipse.jgit.revwalk.RevCommit
import java.time.ZoneId

object SshCallBack extends TransportConfigCallback {

  override def configure(t: Transport | Null): Unit = {
    t.asInstanceOf[SshTransport]
      .setSshSessionFactory(SshSessionFactory.getInstance())
  }

}

extension (g: Git) {
  def git_add_all(): Unit = {
    import language.unsafeNulls
    g.add().addFilepattern(".").call()
  }

  def git_push(): Unit = {
    import language.unsafeNulls
    g.push()
      .setTransportConfigCallback(SshCallBack)
      .call()
  }

  def git_commit(
      msg: String,
      name: String,
      email: String
  ): Unit = {
    import language.unsafeNulls
    g.commit()
      .setAllowEmpty(false)
      .setAll(true)
      .setAuthor(name, email)
      .setMessage(msg)
      .call()
  }

}

extension (c: RevCommit) {
  def getLocalCommitTime: LocalDateTime = {
    import language.unsafeNulls
    Instant
      .ofEpochSecond(c.getCommitTime)
      .atZone(ZoneId.systemDefault)
      .toLocalDateTime
  }
}
