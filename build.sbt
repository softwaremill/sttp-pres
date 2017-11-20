import sbt._
import Keys._

name := "sttp-pres"
organization := "com.softwaremill"
scalaVersion := "2.12.4"

val sttpVersion = "1.0.4"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp" %% "core" % sttpVersion,
  "com.softwaremill.sttp" %% "akka-http-handler" % sttpVersion,
  "com.softwaremill.sttp" %% "async-http-client-handler-monix" % sttpVersion
)
