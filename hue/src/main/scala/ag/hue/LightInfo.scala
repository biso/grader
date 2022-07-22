package ag.hue

import upickle.default._

case class LightInfo(
    state: LightState,
    `type`: String,
    name: String,
    modelid: String,
    manufacturername: String,
    productname: String,
    capabilities: LightCapabilities,
    config: ujson.Value,
    uniqueid: String,
    swversion: String
) derives ReadWriter
