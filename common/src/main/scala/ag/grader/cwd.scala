package ag.grader

// The current working directory is just a path
opaque type Cwd = os.Path

/** Returns the the path of the current Cwd in our context */
def cwd()(using it: Cwd): os.Path = it

/** Sets the contextual Cwd for the execution of f
 *     
 *      Example:
 *          cd(os.pwd / "hello") {
 *              // the code in this block runs in a context in which Cwd is
 *              //      os.pwd/"hello"
 *          }
 */
def cd[A](it: os.Path)(f: Cwd ?=> A): A = f(using it)
