package ag.grader

/** Universal extensions added to all types
  */
extension [A](something: A) {

  /** adds a convenient pipe operator Allows me to write "hello" |> println
    * instead of println("hello") and a |> f |> g instead of g(f(a))
    */
  def |>[B](f: A => B): B = f(something)
}
