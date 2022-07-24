package ag.grader

import munit.FunSuite
import munit.ScalaCheckSuite

import java.security.MessageDigest

import org.scalacheck.Prop._
import scala.math.Ordering.Implicits.given
import upickle.default.*

class StateTests extends FunSuite {

  given canThrowShellException: CanThrow[ShellException] =
    compiletime.erasedValue

  val worlds = FunFixture[World](
    setup = { test =>
      val d = os.temp.dir(deleteOnExit = true)
      World(
        classOf[Config] -> SimpleConfig(
          gitHost = "127.0.0.01",
          gitUser = "?",
          gitPort = 666,
          threads = 1,
          optBaseDir = Some(d),
          reportDomain = "example.com",
          reportDefaultUser = "bob"
        )
      )
    },
    teardown = { _.close() }
  )

  worlds.test("config sanity") { implicit world =>
    val config = Config.get()
    assert(clue(os.isDir(config.workDir)))
  }

  worlds.test("cwd") { implicit world =>
    val config = Config.get()
    cd(config.workDir) {
      assert(clue(config.workDir.toString) == clue(cwd().toString))
    }
    cd(config.workDir) {
      val (out, _) = sh("pwd")
      val dir = os.read.lines(out).head
      assert(clue(dir).endsWith(clue(config.workDir.toString)))
    }
  }

}

class TestState extends FunSuite {
  test("cwd") {
    val t: os.Path = os.root
    cd(t) {
      assert(clue(t.toString) == clue(cwd().toString))
    }
  }
}

class TestStudentId extends ScalaCheckSuite {
  property("wrapping") {
    forAll { (s: String) =>
      val w = Name[Student](s)
      assert(clue(w.toString) == clue(s))
    }
  }
  property("compare") {
    forAll { (a: String, b: String) =>
      val p = Name[Student](a)
      val q = Name[Student](b)

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
      val id = Name[Student](a)
      val as_json = write(id)
      val as_binary = writeBinary(id)
      assert(clue(read[Name[Student]](as_json)) == clue(id))
      assert(clue(readBinary[Name[Student]](as_binary)) == clue(id))
    }

  }
}

class TestPickle extends FunSuite {

  test("null") {
    import upickle.default._

    case class Bob(name: String | Null) derives ReadWriter, CanEqual
    val b1 = Bob(name = null)
    val bs = write(b1)
    val b2 = read[Bob](bs)
    assert(clue(b1) == clue(b2))
  }
}

class TestMdExtensions extends ScalaCheckSuite {
  property("Basic properties") {
    forAll { (s:String) =>
      val md1 = MessageDigest.getInstance("SHA1").nn
      val out1 = md1.update_string(s).digest().nn.toSeq
      val md2 = MessageDigest.getInstance("SHA1").nn
      for (c <- s) md2.update_string(c.toString)
      val out2 = md2.digest().nn.toSeq
      assert(clue(out1) == clue(out2))
      val md3 = md1.update_string("x")
      val out3 = md3.digest().nn.toSeq
      assert(clue(out3) != clue(out1))
    }
  }
}
