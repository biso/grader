package ag.grader

import upickle.default._
import scala.util.matching.Regex

case class Weight(pattern: String, weight: Double) derives ReadWriter {
  lazy val regex: Regex = pattern.r

  def matches(test_name: String): Option[Double] =
    if (regex.matches(test_name)) Some(weight) else None
}
