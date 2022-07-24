package ag.grader

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

/** Convenient extension methods for Java's MessageDigest
 */
extension (md: MessageDigest) {
  /** Append a string
   * @param s the string to append
   */
  def update_string(s: String): MessageDigest = {
    md.update(s.getBytes(StandardCharsets.UTF_8))
    md
  }

  /** Recursively append the contents of the given directory
   * @param path the directory
   */
  def update_contents(path: os.Path): MessageDigest = {
    update_string(path.last)
    if (os.isFile(path, followLinks=false)) {
      md.update_string("F")
      /* what if the file is big? */
      md.update(os.read.bytes(path))
    } else if (os.isLink(path)) {
      md.update_string("L")
      md.update_string(os.readLink(path).toString)
    } else if (os.isDir(path, followLinks=false)) {
      md.update_string("D")
      os.list(path, sort = true).foreach(p => md.update_contents(p))
    } else {
      ???
    }
    md
  }
}
