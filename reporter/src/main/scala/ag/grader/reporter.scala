package ag.grader

import language.experimental.saferExceptions
import java.time.LocalDateTime
import scala.util.{Failure, Success, Try}
import scala.collection.immutable.TreeMap
import org.eclipse.jgit.api.errors.InvalidRemoteException
import os.PermSet

import scala.util.Using
import scala.math.Ordering.Implicits.given

// runs on a CS machine
//
// - enroll new students in class
// - manage updates to students' public keys
// - send e-mail notifications for:
//     * key updates
//     * new enrollment
//     * submission reports
// - update web page
//
// Student enrollment information is kept in 3 places
//
// (1) The dropbox; a shared directory in which students can securely copy and update their public keys
// (2) The gitolite/keydir directory; public keys are copied their in order to give students access to gitolite
// (3) The <course_name>__enrollments repo; contains information about all enrolled students
//
// A student joins the class by copying their ssh public key to the dropbox.
//    - permissions should be t+rwx____wx
//           t => only the owner of a file can delete it
//           rwx => read/write/execute by me
//           w => writable by others (there is a risk that someone will implement a DOS attack
//                that fills it with junk)
//           x => navigable by others so they can work with their keys
//
//    - extra checks:
//          * the key for student (e.g. bob) should be named bob.pub
//          * the owner for the file should also be bob
//          * we will assume that if the file name and the owner match then the file was created by that owner
//            this leaves us with the possibility of someone with "chown" capabilities cheating the system and
//            claiming to be someone else but this attack can cause significantly more damage in our environment
//
//    - a student can later update their key in the dropbox (e.g. if they lost the private key)
//
//    - a student can delete their key to indicate that they're dropping the class
//          * only the owner of the key (the student), the owner of the dropbox (me), or a superuser can
//            delete a key
//
//    - you don't have to be enrolled in the class in order to add your key; all you need is a CS account. This
//      allows curious students, students from previous years, future students, and others to participate but
//      so far this hasn't been a problem. If it does, I'll stop making the dropbox world-writeable and complicate
//      things for all involved
//
// This program runs periodically and synchronizes the dropbox with the other 2 places
//
//     - if the key doesn't exist in gitolite or if it does exist but it's different then the key from
//       the dropbox is copied to gitolite-admin/keys and the change is pushed to gitolite
//     - if the key doesn't exist in the <course_name>__enrollments repo then that student name is copied
//       and the repo is pushed to gitolite
//
// This means that the 3 locations might be out of sync:
//     - a key is making its way from the dropbox to the other places. This is just a few minute delay that is
//       generally not a source of problems
//     - a key is deleted from the dropbox. We current don't delete the others.
//
// Where should I look to a get a list of all students in the class? It depends:
//     - if you want the active students (those who didn't remove their key), look in the dropbox
//     - if you want to see all students including those who left, look in the enrollments directory
//     - never look in gitolite-admin/keys because it contains the keys for everyone who has gitolite access
//



@main def reporter(args: String*): Unit = {
  val what = if (args.isEmpty) "" else args(0)

  def should_consider(name: String): Boolean = name.startsWith(what)

  Given(World()) {

    given canThrowShellException: CanThrow[
      GitException | GitoliteException | KnownRepoException | ShellException
    ] =
      compiletime.erasedValue

    val config: Config = Config.get()

    import config._

    /////////////////////////
    // Update remote repos //
    /////////////////////////

    updateRemoteRepos(should_consider)

    //////////////////////////////////////
    // Load information for all courses //
    //////////////////////////////////////

    val allActiveCoursesSorted =
      Courses.load(c => should_consider(c.id)).toList.sortBy(_.name)

    /////////////////
    // Check repos //
    /////////////////

    echo("checking repos")

    GitoliteRepo.check(false)

    allActiveCoursesSorted.foreach { course =>
      // We must have the enrollment repo before we attempt to find all students
      EnrollmentRepo(course).check(true)
      course.knownRepos().foreach(_.check(true))
    }

    /////////////////////////////////////////////////
    // update keys in gitolite and enrollment repo //
    /////////////////////////////////////////////////

    update_keys(allActiveCoursesSorted)

    //////////
    // HTML //
    //////////

    echo("generating HTML")

    allActiveCoursesSorted.foreach { course =>
      generate_html(course)
    }

    //////////////////////////
    // Send student updates //
    //////////////////////////

    echo("sending emails")

    send_updates(allActiveCoursesSorted)

    trace("shutdown")
  }

}
