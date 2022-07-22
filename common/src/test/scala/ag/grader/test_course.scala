package ag.grader

import munit.FunSuite
import munit.ScalaCheckSuite

import org.scalacheck.Prop._
import scala.math.Ordering.Implicits.given
import upickle.default._

class CourseTests extends FunSuite {
  val with_courses = FunFixture[String](
    setup = { test =>
      """|{
         |  "a" : {
         |      "send_to_students": true,
         |      "update_site": false,
         |      "active": true,
         |      "projects": {}
         |  }
         |}
      """.stripMargin
    },
    teardown = { _ => }
  )

  with_courses.test("read courses from json") { text =>
    val courses = read[Map[String, CourseData]](text)
    assert(clue(courses.keys.toList.sorted) == clue(List("a")))
    val da = courses("a")
    assert(clue(da.active) == clue(true))
    assert(clue(da.update_site) == clue(false))
    assert(clue(da.send_to_students) == clue(true))
    val projects = da.projects
    assert(clue(projects.isEmpty))
  }

}

class CourseIdTests extends ScalaCheckSuite {
  property("wrapping") {
    forAll { (s: String) =>
      val w = CourseId(s)
      assert(clue(w.toString) == clue(s))
    }
  }
  property("compare") {
    forAll { (a: String, b: String) =>
      val p = CourseId(a)
      val q = CourseId(b)

      if (a < b) assert(clue(p) < clue(q))
      if (a <= b) assert(clue(p) <= clue(q))
      if (a > b) assert(clue(p) >= clue(q))
      if (a >= b) assert(clue(p) >= clue(q))
      if (a == b) assert(clue(p) == clue(q))
      if (a != b) assert(clue(p) != clue(q))
    }
  }
  property("json") {
    forAll { (a: String) =>
      val id = CourseId(a)
      val as_json = write(id)
      val as_binary = writeBinary(id)
      assert(clue(read[CourseId](as_json)) == clue(id))
      assert(clue(readBinary[CourseId](as_binary)) == clue(id))
    }

  }
}
