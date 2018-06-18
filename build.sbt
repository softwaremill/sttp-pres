import sbt._
import Keys._

name := "sttp-pres"
organization := "com.softwaremill"
scalaVersion := "2.12.5"

val sttpVersion = "1.2.0-RC6"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp" %% "core" % sttpVersion,
  "com.softwaremill.sttp" %% "akka-http-backend" % sttpVersion,
  "com.softwaremill.sttp" %% "async-http-client-backend-monix" % sttpVersion,
  "com.typesafe.akka" %% "akka-stream" % "2.5.12"
)
