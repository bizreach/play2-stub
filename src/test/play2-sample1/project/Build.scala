import sbt._
import Keys._
import play.Play.autoImport._
import play.PlayImport.PlayKeys._

object ApplicationBuild extends Build {

  val main = Project("root", file("."))
    .enablePlugins(play.PlayScala)
    .settings(
      scalaVersion := "2.11.2",
      playDefaultPort := 9001,
      libraryDependencies ++= Seq(
        "jp.co.bizreach" %% "play2-handlebars" % "0.2.0"
      )
    )
}
