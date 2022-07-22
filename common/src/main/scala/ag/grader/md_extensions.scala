package ag.grader

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

extension (md: MessageDigest) {
  def update(s: String): Unit = {
    md.update(s.getBytes(StandardCharsets.UTF_8))
  }

  def update(path: os.Path): Unit = {
    update(path.last)
    if (os.isFile(path)) {
      md.update("F")
      /* what if the file is big? */
      md.update(os.read.bytes(path))
    } else if (os.isLink(path)) {
      md.update("L")
      md.update(os.readLink(path).toString)
    } else if (os.isDir(path)) {
      md.update("D")
      os.list(path, sort = true).foreach(p => md.update(p))
    } else {
      ???
    }
  }
}
