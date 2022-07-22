package ag

import requests.RequestBlob
import upickle.default._

import scala.util.{Failure, Success, Try}

package object hue {

  implicit class Extra[A](a: A) {

    def |>[B](f: A => B): B =
      f(a)

    def /(nm: String)(using A =:= ujson.Value): ujson.Value =
      a.asInstanceOf[ujson.Value].obj(nm)

    def $(nm: String)(using A =:= ujson.Value): String =
      a.asInstanceOf[ujson.Value].obj(nm).str

    def get[T: Reader](using A =:= String): T = {
      val text = requests.get(a).text()
      Try(read[T](text)) match {
        case Success(x) =>
          x
        case Failure(e) =>
          println(s"failed to parse $text")
          throw e
      }
    }
    def put[T: Reader](data: RequestBlob)(using A =:= String): T =
      read[T](requests.put(a, data = data).text())
    def post[T: Reader](data: RequestBlob)(using A =:= String): T =
      read[T](requests.post(a, data = data).text())

  }

  //def get[T : Reader](url : String) : T = read[T](requests.get(url).text)

  //def post[T : Reader](url : String) : T = read[T](requests.post(a, data = write(Map("devicetype" -> "app#browser"))).text

  // https://www.cs.rit.edu/~ncs/color/API_JAVA/XYZSet.java

  def rgbToxy(r: Double, g: Double, b: Double): (Double, Double) = {
    val rf = 0.0039215f * r
    val gf = 0.0039215f * g
    val bf = 0.0039215f * b

    val x = 0.431f * rf + 0.342f * gf + 0.178f * bf
    val y = 0.222f * rf + 0.707f * gf + 0.071f * bf
    val z = 0.020f * rf + 0.130f * gf + 0.939f * bf
    val s = x + y + z

    (x / s, y / s)
  }

}
