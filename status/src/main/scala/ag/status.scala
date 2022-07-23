import language.experimental.saferExceptions

import ag.grader.*
import ag.hue.*

import upickle.default.*

@main def status(): Unit = {
  given World = World()
  given canThrowShellException
      : CanThrow[GitException | GitoliteException | ShellException] =
    compiletime.erasedValue
  updateRemoteRepos(_ => true)
  val projectNameToLight =
    read[Map[String, String]](os.read(os.pwd / "lights.json"))

  given Config = Config.get()

  for {
    c <- Courses.load(_ => true)
    p <- c.projects
    l <- projectNameToLight.get(p.fullName)
  } {
    val chosen = p.data.chosen.toSet
    val results = p.resultsDir

    var happy = 0.0
    var sad = 0.0

    for {
      d <- os.list(results)
      if os.isDir(d)
      if !d.last.startsWith(".")
      s <- os.list(d)
      if os.isFile(s)
      if s.last == "summary"
      outcome = read[OutcomeData](os.read(s))
      (test_name, outcome) <- outcome.tests
      if chosen(test_name)
    } yield {
      if (outcome == "pass") {
        happy += 1
      } else if (outcome == "fail") {
        sad += 1
      }
    }

    val all = c.students.keySet.size.toDouble * chosen.size
    // val all = happy + sad
    val unknown = all - happy - sad

    val hr = happy / all
    val sr = sad / all
    val ur = unknown / all

    val r = (hr + sr)
    val g = hr
    val b = (hr + ur)

    for {
      hub <- Hue.login
      light <- hub.getLights
      if light.info.name == l
    } {
      light.setRGB(r, g, b)
      light.setBRI(20)
    }

    println(s"${p.fullName} $hr $ur $sr")

  }

  println("status")
}
