package ag.grader

def Given[T <: AutoCloseable, A](t: T)(f: T ?=> A): A = {
  try {
    f(using t)
  } finally {
    t.close()
  }
}
