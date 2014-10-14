import sbt._
import Keys._
import play.Play.autoImport._

object ApplicationBuild extends Build {

  lazy val main = Project("root", file("."))
    .enablePlugins(play.PlayScala)
    .settings(
      scalaVersion := "2.11.2",
      resolvers ++= Seq(
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
        "Maven Central Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
      ),
      libraryDependencies ++= Seq(
        // WS is needed for proxying requests
        ws,
        "jp.co.bizreach" %% "play2-stub" % "0.1-SNAPSHOT"
      )
    )
}
