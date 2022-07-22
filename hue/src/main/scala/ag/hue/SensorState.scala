package ag.hue

import upickle.default._

trait SensorState {
  
}

object SensorState {

  case class DayLight(
    daylight: Boolean,
    lastupdated: String
  ) extends SensorState
      derives ReadWriter

  case class Temperature(
      temperature: Int,
      lastupdated: String
  ) extends SensorState
      derives ReadWriter

  case class LightLevel(
      dark: Boolean,
      daylight: Boolean,
      lightlevel: Int,
      lastupdated: String
  ) extends SensorState
      derives ReadWriter

  case class Presence(
      presence: Boolean,
      lastupdated: String
  ) extends SensorState
      derives ReadWriter

  case class ClipGenerics(
      status: Int,
      lastupdated: String
  ) extends SensorState
      derives ReadWriter

  case class Switch(
      buttonevent: Int,
      lastupdated: String
  ) extends SensorState
      derives ReadWriter


}
