package ag.grader

import upickle.default._

/** Raw course data. Loaded directely from json */
case class CourseData(
    /** is it active */
    active: Boolean,
    /** should we send reports to students */
    send_to_students: Boolean,
    /** should we update the web site */
    update_site: Boolean,
    /** a list of projects */
    projects: Map[String, ProjectData]
) derives ReadWriter
