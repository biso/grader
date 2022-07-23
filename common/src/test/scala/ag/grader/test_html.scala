package ag.grader

import munit.FunSuite
import scala.collection.mutable

class HtmlTests extends FunSuite {

  class TestContext extends HtmlContext with AutoCloseable {

    val sb = mutable.StringBuilder()

    override def close(): Unit = {}

    override def start(tag: String, attrs: Seq[(String, String)]) = {
      sb.append("+")
      sb.append(tag)
      attrs.foreach { (k, v) =>
        sb.append(":")
        sb.append(k)
        sb.append("=")
        sb.append(v)
      }
    }

    override def end(tag: String) = {
      sb.append("-")
      sb.append(tag)
    }

    override def text(s: String) = {
      sb.append("@")
      sb.append(s)
    }

  }
  test("html") {
    val ctx = TestContext()
    html(ctx) {
      body {
        text("hello")
      }
    }
    assert(clue(ctx.sb.toString) == clue("+html+body@hello-body-html"))
  }

  test("attributes") {
    val ctx = TestContext()
    html(ctx) {
      body.attr("a", null).attr("b", "z") {}
    }
    assert(clue(ctx.sb.toString) == clue("+html+body:b=z-body-html"))
  }

  test("element") {
    val ctx = TestContext()
    html(ctx) {
      Element("x") {}
    }
    assert(clue(ctx.sb.toString) == clue("+html+x-x-html"))
  }

  test("simple_table") {
    val ctx = TestContext()
    given HtmlContext = ctx
    simple_table(
      Seq(text("a"), text("b")),
      Seq(text("c"), text("d"))
    )
    assert(
      clue(ctx.sb.toString) == clue(
        "+table+tr+td@a-td+td@b-td-tr+tr+td@c-td+td@d-td-tr-table"
      )
    )
  }

}
