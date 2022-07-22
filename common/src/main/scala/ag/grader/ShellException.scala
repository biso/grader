package ag.grader

case class ShellException(
    cnt: Long,
    running: Seq[os.Shellable],
    out: os.Path,
    err: os.Path
) extends Exception {

  override def getMessage: String =
    s"[$cnt] ${running.flatMap(_.value).mkString(" ")}\n   look in $out and\n           $err"

}
