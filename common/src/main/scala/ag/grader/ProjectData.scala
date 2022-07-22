package ag.grader

import java.time.LocalDateTime

import upickle.default.ReadWriter

case class ProjectData(
    active: Boolean,
    ignore_tests: Boolean,
    docker_file: Option[String],
    test_cutoff: LocalDateTime,
    code_cutoff: LocalDateTime,
    test_extensions: Seq[String],
    chosen: Seq[String],
    weights: Seq[Weight],
    bad_tests: Set[String]
) derives ReadWriter
