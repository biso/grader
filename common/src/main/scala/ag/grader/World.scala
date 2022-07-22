package ag.grader

import collection.mutable
import scala.reflect.ClassTag

// The simplest possible dependency injection implementation I could think of.
//
// Why? services and components should depend on the interface as opposed
//      to the implementation of other services and components.
//
//      Details about the component's implementation of life-cycle should be
//      implemented by the component not its clients
//
// Passes an implicit instance of World around and look things up in it.

class WorldException extends Exception

enum WorldState derives CanEqual {
  case Open    /* open for lookup and creation */,
       Closing /* closing, can lookup but not create */,
       Closed  /* closed, can't do anything with it */
}

case class BadWorldState(state: WorldState, expected: Seq[WorldState]) extends WorldException

class World(initial: (Any, Any)*) extends AutoCloseable {
  private val data = mutable.Map[Any, Any](initial: _*)
  private var stack = List[Any]()
  private var state = WorldState.Open

  private def stateIn(states: WorldState*): Unit = synchronized {
    if (!states.contains(state)) throw new BadWorldState(state, states)
  }

  def get[A: ClassTag](factory: => A): A = synchronized {
    val key = summon[ClassTag[A]].runtimeClass
    stateIn(WorldState.Open, WorldState.Closing)
    data
      .getOrElseUpdate(
        key, {
          stateIn(WorldState.Open)
          val a = factory
          stack = a +: stack
          a
        }
      )
      .asInstanceOf[A]
  }

  def close(): Unit = synchronized {
    if (state != WorldState.Open) return
    stack.foreach {
      case c: AutoCloseable =>
        c.close()
      case x =>
    }
  }
}
