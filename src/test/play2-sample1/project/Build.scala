import sbt._
import Keys._
import play.sbt.Play.autoImport._
import play.sbt.PlayImport.PlayKeys._
import play.sbt.PlayScala

object ApplicationBuild extends Build {

  val main = Project("root", file("."))
    .enablePlugins(PlayScala)
    .settings(
      scalaVersion := "2.11.12",
      playDefaultPort := 9001,
      libraryDependencies ++= Seq(
        "jp.co.bizreach" %% "play2-handlebars" % "0.3.1"
      )
    )
}
