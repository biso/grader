package ag.grader

import upickle.default.write

class StudentIdTests extends munit.FunSuite {
  test("t1") {
    val s = StudentId("ag")
    write(s) |> println
    write("ag") |> println
    assert(clue(write(s)) == clue("\"ag\""))
  }
}