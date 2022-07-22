package ag.hue

import upickle.default._

case class LightState(
    on: Boolean,
    bri: Int = 0,
    ct: Int = -1,
    alert: String,
    colormode: String = "",
    mode: String,
    reachable: Boolean
) derives ReadWriter
