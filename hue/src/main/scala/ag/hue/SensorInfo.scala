package ag.hue

import upickle.default._

case class SensorInfo(
    name: String,
    `type`: String,
    modelid: String,
    manufacturername: String,
    swversion: String,
    state: ujson.Value,
    config: ujson.Value
) derives ReadWriter {

  val the_state: SensorState = {
    `type` match {
      case "Daylight"          => read[SensorState.DayLight](state)        
      case "ZLLTemperature"    => read[SensorState.Temperature](state)
      case "ZLLPresence"       => read[SensorState.Presence](state)
      case "ZLLLightLevel"     => read[SensorState.LightLevel](state)
      case "CLIPGenericStatus" => read[SensorState.ClipGenerics](state)
      case "ZLLSwitch"         => read[SensorState.Switch](state)
      case x                   => throw new Exception(x)
    }
  }
}
