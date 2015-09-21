import org.scalastyle.sbt._
import sbt.Keys._

// -----------------------------------------------------
// Imports
// -----------------------------------------------------

scalariformSettings

Seq(Revolver.settings: _*)

// -----------------------------------------------------
// Variables
// -----------------------------------------------------

lazy val akkaVersion = "2.3.12"

lazy val compileScalastyle: TaskKey[Unit] = taskKey[Unit]("compileScalastyle")

// -----------------------------------------------------
// Overrides
// -----------------------------------------------------

name := "work-distributor"

version := "1.0"

scalaVersion := "2.11.7"

autoScalaLibrary := false

dependencyOverrides ++= Set(
  "org.scala-lang" % "scala-library"  % scalaVersion.value,
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scala-lang" % "scala-reflect"  % scalaVersion.value
)

unmanagedSourceDirectories in Compile := (scalaSource in Compile).value :: Nil

unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil

parallelExecution in Test := false

mainClass in Revolver.reStart := Option("co.s4n.work.distributor.Main")

fork in test := true

scalastyleFailOnError := false

compileScalastyle := ScalastylePlugin.scalastyle.in(Compile).toTask("").value

// -----------------------------------------------------
// Appends
// -----------------------------------------------------

scalacOptions ++= Seq(
  "-unchecked", "-deprecation", "-Xlint", "-Ywarn-dead-code", "-language:_", "-target:jvm-1.7", "-encoding", "UTF-8"
)

resolvers ++= Seq(
  "typesafe repo"       at "http://repo.typesafe.com/typesafe/releases",
  "spray repo"          at "http://repo.spray.io",
  "sonatype oss repo"   at "http://oss.sonatype.org/content/repositories/releases",
  "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"
)

libraryDependencies ++= akkaLibraries ++ functionalLibraries ++ testingLibraries ++ apacheCommonsLibraries ++ utilLibraries

(compile in Compile) <<= (compile in Compile) dependsOn compileScalastyle

// -----------------------------------------------------
// Funciones auxiliares
// -----------------------------------------------------

def akkaLibraries = Seq(
  "com.typesafe.akka"   %% "akka-actor"                    % akkaVersion withSources() withJavadoc(),
  "com.typesafe.akka"   %% "akka-slf4j"                    % akkaVersion withSources() withJavadoc()
)

def functionalLibraries = Seq(
  "org.scalaz" %% "scalaz-core" % "7.1.3" withSources() withJavadoc()
)

def testingLibraries = Seq(
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test" withSources() withJavadoc(),
  "org.scalatest"     %% "scalatest"    % "2.2.4"     % "test",
  "org.scalacheck"    %% "scalacheck"   % "1.12.4"    % "test",
  "org.specs2"        %% "specs2-junit"  % "2.4.2"      % "test",
  "org.specs2"        %% "specs2"        % "2.4.2"      % "test"
)

def apacheCommonsLibraries = Seq(
  "commons-io"         % "commons-io"    % "2.4",
  "commons-codec"      % "commons-codec" % "1.9",
  "org.apache.commons" % "commons-lang3" % "3.3.2"
)

def utilLibraries = Seq(
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.1.0" withSources() withJavadoc(),
  "ch.qos.logback"              % "logback-classic" % "1.1.3",
  "joda-time"                   % "joda-time"       % "2.5",
  "org.joda"                    % "joda-convert"    % "1.2"
)
