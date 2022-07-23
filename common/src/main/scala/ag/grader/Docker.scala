package ag.grader

import java.security.MessageDigest
import scala.collection.mutable

object Docker {

  private val cache = mutable.Map[os.Path, String]()

  def of(docker_file: os.Path)(using World): String =
    cache.synchronized {
      cache.getOrElseUpdate(
        docker_file, {
          if (!os.exists(docker_file)) {
            fatal(s"$docker_file does not exist")
          }

          val sha = (for {
            md <- Maybe(MessageDigest.getInstance("MD5"))
            bytes = os.read.bytes(docker_file)
            d <- Maybe(md.digest(bytes))
          } yield d).get()

          // val sha = MessageDigest
          //  .getInstance("MD5")
          //  .nn
          //  .digest(os.read(docker_file).getBytes)
          //  .nn
          val sha_string = sha.toList.map(b => f"$b%02x").mkString
          val image_name = s"grader:$sha_string"

          val existing =
            os.proc("docker", "images", "-q", image_name).call().out.lines()
          if (existing.isEmpty) {
            println(s"$image_name not found, creating")
            os.proc("docker", "build", "-t", image_name, "-f", docker_file, ".")
              .call(
                cwd = os.temp.dir(deleteOnExit = true),
                check = true,
                stdout = os.Inherit,
                stderr = os.Inherit
              )
          } else if (existing.size > 1) {
            echo(s"    found ${existing.size} of them")
            existing.sorted.foreach { n =>
              echo(s"        $n")
            }
            fatal("too many images")
          }
          image_name
        }
      )
    }

}
