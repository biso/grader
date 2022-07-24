package ag.grader

import munit.FunSuite
import upickle.default.*

class MaybeTests extends FunSuite {

  def f(x: Maybe[String]): Maybe[Int] = {
    x.map(_ => 7)
  }

  test("maybe") {

    val ma = Maybe(10)
    val nil: Maybe[Int] = Maybe(null)

    assert(Maybe(null).map((x: Int) => x + 1).isEmpty())
    assert(ma.map(_ + 1).get() == 11)

    assert(clue(f(Maybe("10"))) == clue(Maybe(7)))

    assert(clue(Maybe(10).get()) == clue(10))
    assert(Maybe(null).getOrElse(5) == 5)

    assert(ma.to[List] == List(10))
    assert(ma.to[Option] == Some(10))

    assert(nil.to[List] == List())
    assert(nil.to[Option] == None)

    assert(clue(writeJs(nil).isNull))
    val majs = writeJs(ma)
    assert(clue(!majs.isNull))
    assert(clue(majs.num) == clue(10))
    
    assert(clue(read[Maybe[Int]](majs).get()) == clue(10))

    val majs2 = writeJs(Maybe(ma))
    
    assert(
      clue(
        read[Maybe[Int]](majs2)
      ) == ma
    )
    assert(clue(read[Maybe[String]](ujson.Null).isEmpty()))

    val ma2 = read[Maybe[Maybe[Int]]](majs)
    assert(clue(ma2.get()) == ma)
    assert(clue(ma2.get().get()) == 10)

    println(ma)
    println(Maybe(ma))
  }

}
