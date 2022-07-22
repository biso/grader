package ag.grader

opaque type Cwd = os.Path

def cwd()(using it: Cwd): os.Path = it

def cd[A](it: os.Path)(f: Cwd ?=> A): A = f(using it)
