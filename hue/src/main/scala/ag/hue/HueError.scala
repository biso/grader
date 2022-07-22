package ag.hue

import upickle.default._

case class HueError(`type`: Int, address: String, description: String)
    derives ReadWriter
