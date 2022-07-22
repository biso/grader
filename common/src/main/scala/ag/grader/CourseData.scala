package ag.grader

import upickle.default._

case class CourseData(
    active: Boolean,
    send_to_students: Boolean,
    update_site: Boolean,
    projects: Map[String, ProjectData]
) derives ReadWriter
