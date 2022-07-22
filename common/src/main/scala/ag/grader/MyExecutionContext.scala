package ag.grader

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

class MyExecutionContext(nThreads: Int)
    extends ExecutionContext
    with AutoCloseable {
  val executor: ExecutorService =
    Executors.newFixedThreadPool(nThreads).nn

  def execute(runnable: Runnable): Unit =
    executor.execute(runnable)

  def reportFailure(cause: Throwable): Unit =
    cause.printStackTrace()

  def close(): Unit = {
    executor.shutdownNow()
  }

}

object MyExecutionContext {
  inline def get()(using world: World): ExecutionContext =
    world.get[MyExecutionContext] {
      MyExecutionContext(Config.get().threads)
    }
}
