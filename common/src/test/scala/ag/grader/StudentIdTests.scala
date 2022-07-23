package ag.grader

import upickle.default.write

class StudentIdTests extends munit.FunSuite {
  test("t1") {
    val s = Name[Student]("ag")
    assert(clue(write(s)) == clue("\"ag\""))
  }
}
