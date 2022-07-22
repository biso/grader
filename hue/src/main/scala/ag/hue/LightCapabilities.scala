package ag.hue

import upickle.default._

case class LightCapabilities(
    certified: Boolean,
    control: ujson.Value,
    streaming: ujson.Value
) derives ReadWriter
