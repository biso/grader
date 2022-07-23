package ag.grader

@main def main() = {
  given World = World()
  given canThrowShellException
      : CanThrow[GitException | GitoliteException | ShellException] =
    compiletime.erasedValue
  updateRemoteRepos(_ => true)
}
