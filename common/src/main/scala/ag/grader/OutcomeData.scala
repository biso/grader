package ag.grader

import upickle.default._

case class OutcomeData(
    tests: Map[String, String],
    isLate: Boolean = false,
    spamon: Boolean = true,
    isMine: Boolean = false
) derives ReadWriter