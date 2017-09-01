import sbt._
import Keys._

name := "sttp-pres"
organization := "com.softwaremill"
scalaVersion := "2.12.3"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp" %% "core" % "0.0.9",
  "com.softwaremill.sttp" %% "akka-http-handler" % "0.0.9",
  "com.softwaremill.sttp" %% "async-http-client-handler-monix" % "0.0.9"
)
