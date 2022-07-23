package ag.grader

import munit.FunSuite

class MaybeTests extends FunSuite {

  def f(x: Maybe[String]): Maybe[Int] = {
    x.map(_ => 7)
  }

  test("maybe") {

    val ma = Maybe(10)
    val nil = Maybe(null)

    assert(Maybe(null).map((x: Int) => x + 1).isEmpty())
    assert(ma.map(_ + 1).get() == 11)

    assert(clue(f(Maybe("10"))) == clue(Maybe(7)))

    assert(clue(Maybe(10).get()) == clue(10))
    assert(Maybe(null).getOrElse(5) == 5)

    assert(ma.to[List] == List(10))
    assert(ma.to[Option] == Some(10))

    assert(nil.to[List] == List())
    assert(nil.to[Option] == None)
  }

}
