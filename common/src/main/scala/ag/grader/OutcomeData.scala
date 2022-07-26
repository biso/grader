package ag.grader

import upickle.default._

/** results from a run */
case class OutcomeData(
    tests: Map[Name[Test], Result],
    isLate: Boolean = false,
    spamon: Boolean = true,
    isMine: Boolean = false
) derives ReadWriter
