import language.experimental.saferExceptions

import ag.grader.*
import ag.hue.*

import upickle.default.*

@main def status(): Unit = Given(World()) {
  // Exceptions that cause fatal errors
  given canThrow: CanThrow[GitException | GitoliteException | ShellException] =
    compiletime.erasedValue

  // Update all repos
  updateRemoteRepos(_ => true)

  //
  // Read the lights configuration file. It should contains
  // a json object of the form:
  //
  // {
  //      "cs429_s22_p4": "light1",
  //      "cs439_s21_p3": "light2"
  // }
  //
  // Indicating the mapping from project name to light name
  //
  val projectNameToLight =
    read[Map[String, String]](os.read(os.pwd / "lights.json"))

  for {

    // All courses
    c <- Courses.load(_ => true)

    // All projects
    p <- c.projects

    // the lights for which project mappings exist
    l <- projectNameToLight.get(p.fullName).toList
  } {
    val chosen = p.data.chosen.toSet
    val results = p.resultsDir

    var happy = 0.0
    var sad = 0.0

    // Compute an RGB color that reflects the status of all submissions for this project
    //    all good => warm white
    //    failures push it towards red
    //    missing submissions push it towards blue
    // This allows me to glance at a few light bulbs in my office and get a feel for how
    // students are doing

    for {
      // All files in the status directory
      d <- os.list(results)

      // only look at directories
      if os.isDir(d)

      // drop hidden files
      if !d.last.startsWith(".")

      // look inside those directories
      s <- os.list(d)

      // Only look at files
      if os.isFile(s)

      // Only look at files named "summary"
      if s.last == "summary"

      // Parse the contents of the file into an OutcomeData class
      outcome = read[OutcomeData](os.read(s))

      // Get all the test results
      (test_name, outcome) <- outcome.tests

      // Only look at chosen tests
      if chosen(test_name)
    } yield {
      if (outcome == "pass") {
        happy += 1
      } else if (outcome == "fail") {
        sad += 1
      }
    }

    // How many chosen tests do we have?
    val all = c.students.keySet.size.toDouble * chosen.size

    // Number of tests without results
    val unknown = all - happy - sad

    // Compute ratios
    val hr = happy / all
    val sr = sad / all
    val ur = unknown / all

    // translate to RGB
    val r = (hr + sr)
    val g = hr
    val b = (hr + ur)

    for {
      // All the Hue hubs on the local network
      hub <- Hue.login

      // All the lights
      light <- hub.getLights

      // Pick the one with the given name (if it exists)
      if light.info.name == l
    } {
      println(s"    updating status for ${p.fullName}")

      // Set color
      light.setRGB(r, g, b)

      // Set brightness
      light.setBRI(20)
    }

    println(s"${p.fullName} $hr $ur $sr")

  }

  println("done")
}
