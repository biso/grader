package ag.grader

import language.experimental.saferExceptions

import scala.util.Try

/** Make sure that the given public key corresponds to a CS student and is enrolled in the class
  *
  * @param course The course
  * @param keyPath path to the file containing the key
  **/
def check_key(course: Course, keyPath: os.Path)(using world: World): Name[Student] throws ShellException = {

  val courseName = course.name.id

  /* assumes the file name is <csid>.pub */
  val studentId =
    Name[Student](keyPath.last.replaceFirst("""\..*""", "").nn)

  /* checks that the file is owned by that CSID. Exposure: some with chown capability can cheat */
  if (
    os.owner(keyPath, followLinks = false).getName.nn != studentId.toString
  ) {
    /* This is a serious security and privacy risk, bail out */
    throw new RuntimeException(
      s"$keyPath is owned by ${os.owner(keyPath)}"
    )
  }

  val config = Config.get()
  val gitoliteDir = config.reposDir / "gitolite-admin"
  val target = gitoliteDir / "keydir" / s"$studentId.pub"

  /* Stay in sync with gitolite (manages git permissions) */
  if (!os.exists(target) || os.read(target) != os.read(keyPath)) {

    cd(gitoliteDir) {
      sh("cp", keyPath, gitoliteDir / "keydir" / s"$studentId.pub")
      Try(sh("git", "add", "*")).map { _ =>
        sh("git", "commit", "-a", "-m", s"added $courseName:$studentId")
        sh("git", "push")
        echo(s"added $courseName:$studentId")
      }
    }


    /* Notify the student */
    val temp = os.temp(perms = os.PermSet.fromString("rw-------"))
    os.write.over(
      temp,
      s"To test connection: ssh -p ${config.gitPort} ${config.gitUser}@${config.gitHost} info"
    )

    Sender.send(
      course,
      "key",
      studentId,
      s"key updated for $studentId",
      temp
    )
  }

  studentId
}

