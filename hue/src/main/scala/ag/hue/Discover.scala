package ag.hue

import upickle.default._

case class Discover(id: String, internalipaddress: String) derives ReadWriter
