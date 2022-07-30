package ag.grader

import upickle.default.*

enum Result {
  case PASS
  case FAIL
  case OTHER(what: String)
}

object Result {
    given ReadWriter[Result] = readwriter[String].bimap[Result](
        {
            case PASS => "pass"
            case FAIL => "fail"
            case OTHER(w) => w
        },
        {
            case "pass" => PASS
            case "fail" => FAIL
            case x => OTHER(x)
        }
    )
}
