
name := "play2-stub"

organization := "jp.co.bizreach"

version := "0.2.2"

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.10.4", "2.11.6")

resolvers ++= Seq(
//  "Local Repository" at "file://" + Path.userHome.absolutePath + "/.ivy2/local",
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Maven Central Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
)

libraryDependencies ++= Seq(
  "com.typesafe.play"  %% "play"                 % "2.3.4"    % "provided",
  "com.typesafe.play"  %% "play-ws"              % "2.3.4"    % "provided",
  "commons-io"          % "commons-io"           % "2.4",
  "jp.co.bizreach"     %% "play2-handlebars"     % "0.2.0",
  "org.scalatest"      %% "scalatest"            % "2.2.1"    % "test",
  "org.mockito"         % "mockito-all"          % "1.9.5"    % "test",
  "com.typesafe.play"  %% "play-test"            % "2.3.3"    % "test"
)

publishMavenStyle := true

publishTo := {
  if (isSnapshot.value)
    Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
  else
    Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

scalacOptions := Seq("-deprecation", "-feature")

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/bizreach/play2-stub</url>
    <licenses>
      <license>
        <name>The Apache Software License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <url>https://github.com/bizreach/play2-stub</url>
      <connection>scm:git:https://github.com/bizreach/play2-stub.git</connection>
    </scm>
    <developers>
      <developer>
        <id>scova0731</id>
        <name>Satoshi Kobayashi</name>
        <email>satoshi.kobayashi_at_bizreach.co.jp</email>
        <timezone>+9</timezone>
      </developer>
    </developers>)
