package ag.grader

class NonRecoverableException(msg: String, cause: Exception|Null) extends Exception(msg, cause) {}
