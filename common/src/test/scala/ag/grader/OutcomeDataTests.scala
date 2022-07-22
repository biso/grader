package ag.grader

import upickle.default._

class OutcomeDataTests extends munit.FunSuite {

  private def check(
      json: String,
      isLate: Boolean,
      isMine: Boolean,
      spamon: Boolean
  ): Unit = {
    val o = read[OutcomeData](json)
    assert(clue(o.isLate) == clue(isLate))
    assert(clue(o.isMine) == clue(isMine))
    assert(clue(o.spamon) == clue(spamon))
  }

  test("read with defaults") {
    check("""{ "tests" : {} }""", false, false, true)
    check("""{ "tests" : {}, "isLate": true}""", true, false, true)
    check("""{ "tests" : {}, "spamon": true}""", false, false, true)
    check("""{ "tests" : {}, "spamon": false}""", false, false, false)
  }

}
