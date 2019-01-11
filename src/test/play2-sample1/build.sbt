name := """sample-app"""

version := "1.0-SNAPSHOT"
import play.sbt.PlayImport.PlayKeys._

lazy val root = (project in file(".")).enablePlugins(PlayScala)
  .settings(
    scalaVersion := "2.11.12",
    playDefaultPort := 9001,
    libraryDependencies ++= Seq(
      "jp.co.bizreach" %% "play2-handlebars" % "0.3.1",
      "com.typesafe.play"  %% "play-test"            % "2.4.2"    % "test",
      specs2 % Test
    )
  )
