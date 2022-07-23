package ag.hue

import java.util.Random

import upickle.default._

import scala.util.Try

object main extends App {

  val login = Hue.login

  val sensors = login.flatMap(_.getSensors).sortBy(_.info.name)
  sensors.foreach { s =>
    println("------")
    println(write(s.info, indent = 3))
    println(s.info.the_state)
  }

  val lights = login.flatMap(_.getLights)
  lights.map(_.info.name).sorted.mkString("\n") |> println
  val blooms = lights.filter(_.info.name.contains("bloom"))
  blooms.map(_.info.name).sorted.mkString("using ", ",", "") |> println

  blooms.foreach(_.setRGB(0, 0, 0))
  Thread.sleep(10000)

  val r = new Random()

  while (true) {
    val s = r.nextInt(30000)
    Thread.sleep(s)
    val red = r.nextInt(100)
    val blue = r.nextInt(100)
    val green = r.nextInt(100)
    println(s"$red $green $blue")
    Try(blooms.foreach(l => l.setRGB(red, green, blue)))
  }

  // lights.foreach(println)

  // login.flatMap(_.getRawSensors).foreach(println)

  // sensors.map(_.info).collect {
  //  case TemperatureSensorInfo(name,_,s : SensorState.Temperature) => name -> s
  // }.foreach(println)

  // sensors.map(_._2).flatMap(s => Try((s $ "name", s / "config" / "battery", s $ "uniqueid")).toOption).foreach(x => println(write(x,4)))

  /*
  lights.
    find(_.info.name == "cs439-f18").
    map(_.setRGB(1,.5,0))

  lights.
    find(_.info.name == "cs439h-f18").
    map(_.setRGB(0,0,0))
   */
  // lights.filter(_.info.name.toLowerCase.startsWith("office")).map(_.setRGB(1,1,.7)) //|> (v => pprint.pprintln(v))

}
