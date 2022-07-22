package ag.hue

import requests.RequestBlob
import upickle.default._
import ujson.Value

case class Light(hue: Hue, id: String, info: LightInfo) derives ReadWriter {

  def setState(data: RequestBlob): Value =
    s"http://${hue.ip}/api/${hue.user}/lights/$id/state".put[Value](data)

  def setState(pairs: (String, Value)*): Value =
    setState(write(pairs.toMap))

  def setXY(x: Double, y: Double): Value =
    setState("xy" -> List(x, y), "on" -> true)

  //def setHS(id : String, h : Int, s : Int) : Value =
  //  setState("hue" -> h, "sat" -> s, "on" -> true)

  def setRGB(r: Double, g: Double, b: Double): Value = if (
    (r == 0) && (g == 0) && (b == 0)
  ) {
    setState(write(Map("on" -> false)))
  } else {
    setState(write(Map("on" -> true)))
    val (x, y) = rgbToxy(r, g, b)
    setXY(x, y)
  }

  def setBRI(v: Int): Value = {
    setState("bri" -> ujson.Num(v))
  }

}
