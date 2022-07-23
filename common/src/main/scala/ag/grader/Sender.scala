package ag.grader

import os.Source.WritableSource

trait Sender {
  def send(
      course: Course,
      topic: String,
      to: Name[StudentData],
      subject: String,
      body: os.Path
  )(using World): Unit
}

object Sender {
  def send(
      course: Course,
      topic: String,
      to: Name[StudentData],
      subject: String,
      body: os.Path
  )(using World): Unit = get().send(course, topic, to, subject, body)

  inline def get()(using world: World): Sender = world.get[Sender] {
    MailSender
  }

}

object InvalidSender extends Sender {
  def send(
      course: Course,
      topic: String,
      to: Name[StudentData],
      subject: String,
      body: os.Path
  )(using World): Unit = {
    throw new Exception("sending is disallowed in this context")
  }
}

class FileSender(outDir: os.Path) extends Sender {

  def send(
      course: Course,
      topic: String,
      to: Name[StudentData],
      subject: String,
      body: os.Path
  )(using World): Unit = {
    val file = outDir / topic / to.toString
    os.write.over(file, s"$subject\n", createFolders = true)
    os.write.append(file, os.read(body))
  }

}

object MailSender extends Sender {

  def send(
      course: Course,
      topic: String,
      to: Name[StudentData],
      subject: String,
      body: os.Path
  )(using World): Unit = {
    val config = Config.get()
    os.proc(
      "mail",
      "-c",
      s"${config.reportDefaultUser}@${config.reportDomain}",
      "-s",
      subject,
      course.send_to(to)
    ).call(stdin = body)

  }

}
