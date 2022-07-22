
lazy val scala3 = "3.2.1-RC1-bin-20220718-67f11ff-NIGHTLY"

val oslib = "com.lihaoyi" %% "os-lib" % "0.8.1"
val upickle = "com.lihaoyi" %% "upickle" % "2.0.0"

inThisBuild(
  Seq(
    version := "0.1",
    scalaVersion := scala3,
    scalacOptions := Seq("-Ysafe-init","-Yexplicit-nulls","-unchecked", "-deprecation"),
    organization := "ag",
    version := "0.1-SNAPSHOT",
    testFrameworks += new TestFramework("munit.Framework"),
    libraryDependencies ++= Seq(),
  ),
)

lazy val common = project.in(file("common")).settings(
  name := "common",

  libraryDependencies ++= Seq(
      "org.eclipse.jgit" % "org.eclipse.jgit" % "6.0.0.202111291000-r",
      "org.eclipse.jgit" % "org.eclipse.jgit.ssh.jsch" % "6.0.0.202111291000-r",
      oslib,
      upickle,
      "com.lihaoyi" %% "sourcecode" % "0.3.0",
      //"org.postgresql" % "postgresql" % "42.2.22",
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test,
      "org.slf4j" % "slf4j-simple" % "1.7.36"
  )
)

lazy val hue = project.settings(
      libraryDependencies ++= Seq(
          oslib,
          upickle,
          "com.lihaoyi" %% "requests" % "0.7.1"
      )
)

//////////////
// Programs //
//////////////

/***** runner *****/
lazy val runner = project.
  dependsOn(common).
  enablePlugins(JavaAppPackaging)

/***** reporter *****/
lazy val reporter = project.
  dependsOn(common).
  enablePlugins(JavaAppPackaging)

/***** prepare ****/
lazy val prepare = project.
  dependsOn(common).
  enablePlugins(JavaAppPackaging)

/***** run *****/
lazy val run = project.
  dependsOn(common).
  enablePlugins(JavaAppPackaging)

/***** sync_reops *****/
lazy val sync_repos = project.
  dependsOn(common).
  enablePlugins(JavaAppPackaging)

lazy val status = project.
  dependsOn(common).
  dependsOn(hue).
  enablePlugins(JavaAppPackaging)
