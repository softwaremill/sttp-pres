package com.softwaremill.demo

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object Server extends App {

  def paramsToString(m: Map[String, String]): String =
    m.toList.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString(" ")

  val serverRoutes: Route =
    pathPrefix("echo") {
      get {
        parameterMap { params =>
          complete(List("GET", "/echo", paramsToString(params))
            .filter(_.nonEmpty)
            .mkString(" "))
        }
      } ~
        post {
          parameterMap { params =>
            entity(as[String]) { body: String =>
              complete(List("POST", "/echo", paramsToString(params), body)
                .filter(_.nonEmpty)
                .mkString(" "))
            }
          }
        }
    } ~ pathPrefix("set_cookies") {
      path("with_expires") {
        setCookie(
          HttpCookie("c",
            "v",
            expires = Some(DateTime(1997, 12, 8, 12, 49, 12)))) {
          complete("ok")
        }
      } ~ get {
        setCookie(
          HttpCookie("cookie1",
            "value1",
            secure = true,
            httpOnly = true,
            maxAge = Some(123L))) {
          setCookie(HttpCookie("cookie2", "value2")) {
            setCookie(
              HttpCookie("cookie3",
                "",
                domain = Some("xyz"),
                path = Some("a/b/c"))) {
              complete("ok")
            }
          }
        }
      }
    }

  implicit val actorSystem: ActorSystem = ActorSystem("sttp-pres")
  import actorSystem.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  Http().bindAndHandle(serverRoutes, "localhost", 51823).map(_ => println("Started"))
}
