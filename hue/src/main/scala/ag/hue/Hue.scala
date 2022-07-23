package ag.hue

import os.Path
import ujson.Value
import upickle.default.*

case class Hue(ip: String, user: String) derives ReadWriter {

  def getLights: Iterable[Light] = {
    s"http://$ip/api/$user/lights".get[Map[String, LightInfo]].map {
      case (id, info) => Light(this, id, info)
    }
  }

  def getSensors: Iterable[Sensor] = {
    s"http://$ip/api/$user/sensors".get[Map[String, SensorInfo]].map {
      case (id, info) => Sensor(this, id, info)
    }
  }

  def getRawSensors: Map[String, Value] =
    s"http://$ip/api/$user/sensors".get[Map[String, ujson.Value]]

  def getSchedules: ujson.Value =
    s"http://$ip/api/$user/schedules".get[ujson.Value]

  def getRules: ujson.Value = s"http://$ip/api/$user/rules".get[ujson.Value]

}

object Hue {

  val hueDir: Path = os.home / ".hue"

  def discover: List[Discover] = {
    // "https://discovery.meethue.com".get[List[Discover]]
    List(
      Discover(id = "001788fffe649974", internalipaddress = "192.168.86.174")
    )
  }

  def login: List[Hue] =
    discover.map { bridge =>
      val bridgeId = bridge.id
      val bridgeIp = bridge.internalipaddress
      val bridgeDir = hueDir / bridgeId
      val userFile = bridgeDir / "user"
      if (!os.exists(userFile)) {
        val reply = s"http://$bridgeIp/api".post[List[ujson.Obj]](
          write(Map("devicetype" -> "app#browser"))
        )

        println(reply)

        val errors = reply.map(_.value.get("error")).collect { case Some(e) =>
          e.obj("description")
        }
        if (errors.nonEmpty) {
          throw new Exception(errors.mkString(","))
        }

        val userName = reply
          .map(_.value.get("success"))
          .collect { case Some(s) => s.obj("username").str }
          .head
        os.write.over(userFile, userName, createFolders = true)

      }
      Hue(bridge.internalipaddress, os.read.lines(userFile).head)
    }

}
