package ag.grader

import munit.FunSuite

class WorldTests extends FunSuite {

  class Thing(val x: String) derives CanEqual

  object Thing {
    def get(using world: World): Thing = world.get(Thing("SIMPLE"))
  }

  test("t1") {
    given World = World()
    val s1 = Thing.get
    val s2 = Thing.get

    assert(clue(s1) == clue(s2))
  }
}
