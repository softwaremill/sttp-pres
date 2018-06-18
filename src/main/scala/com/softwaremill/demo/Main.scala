package com.softwaremill.demo

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.softwaremill.sttp.testing.SttpBackendStub
import monix.eval.Task
import monix.reactive.Observable

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

object Main extends App {
  import com.softwaremill.sttp._

  def sync(): Unit = {
    val req = sttp.get(uri"http://localhost:51823/echo/get")
    implicit val backend = HttpURLConnectionBackend()

    val resp = req.send()
    println(resp.unsafeBody)

    val req2 = sttp.post(uri"http://localhost:51823/echo/post").body("Hello world!")

    val resp2 = req2.send()
    println(resp2.unsafeBody)
  }

  def async(): Unit = {
    val req = sttp.get(uri"http://localhost:51823/echo/get")
    val req2 = sttp.post(uri"http://localhost:51823/echo/post").body("Hello world!")

    implicit val backend = AkkaHttpBackend()

    val resp: Future[Response[String]] = req.send()
    val resp2: Future[Response[String]] = req2.send()

    for {
      r1 <- resp
      r2 <- resp2
    } {
      println(r1)
      println(r2)
      backend.close()
    }
  }

  def order(): Unit = {
    val reqTemplate = sttp
      .header("X-My-Header", "value")
      .response(asByteArray)
      .get(uri"http://example.com") // comment out

    implicit val backend = HttpURLConnectionBackend()

    reqTemplate.send()
  }

  def uriInterpolator(): Unit = {
    println(uri"http://example.com")
    println(uri"http://example.com?x=y")

    val v = "hello world"
    println(uri"http://example.com?x=$v")

    val v1 = None
    val v2 = Some("i'm here")
    println(uri"http://example.com?x=$v1&y=$v2")

    val params = Map("p1" -> 10, "p2" -> 42)
    println(uri"http://example.com?$params&x=y")

    val scheme = "https"
    val domain = "example.com"
    val subdomain = None //Some("www")
    val port = 8080
    println(uri"$scheme://$subdomain.$domain:$port")
  }

  def streamingAkka(): Unit = {
    val stream = Source(List(ByteString("Hello, "), ByteString("Akka Streams")))
    val req = sttp.post(uri"http://localhost:51823/echo/post").streamBody(stream)

    implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend()
    // implicit val backend = HttpURLConnectionBackend // won't compile

    val resp = req.send()

    for {
      r <- resp
    } {
      println(r)
      backend.close()
    }
  }

  def streamingMonix(): Unit = {
    val req = sttp.post(uri"http://localhost:51823/echo/post").body("Hello, Monix!")

    implicit val backend: SttpBackend[Task, Observable[ByteBuffer]] = AsyncHttpClientMonixBackend()

    import monix.execution.Scheduler.Implicits.global

    val resp = req.response(asStream[Observable[ByteBuffer]]).send()

    resp.runAsync.flatMap { r =>
      println(r)

      r.unsafeBody
        .flatMap(bb => Observable.fromIterable(bb.array()))
        .toListL
        .map(bs => new String(bs.toArray, "utf8"))
        .runAsync
    }.map { body =>
      println(body)

      backend.close()
    }
  }

  def requestType(): Unit = {
    val s1: PartialRequest[String, Nothing] = sttp
    val s2: RequestT[Empty, String, Nothing] = sttp

    val s3: Request[String, Nothing] = sttp.get(uri"http://example.com")
    val s4: RequestT[Id, String, Nothing] = sttp.get(uri"http://example.com")

    val s5: PartialRequest[Array[Byte], Nothing] = sttp.response(asByteArray)
    val s6: RequestT[Empty, Array[Byte], Nothing] = sttp.response(asByteArray)

    val s7: PartialRequest[String, Source[ByteString, Any]] = sttp.streamBody(Source.single(ByteString("x")))
  }

  def deserializeResponses(): Unit = {
    val req = sttp.get(uri"http://localhost:51823/echo/get")
    implicit val backend = HttpURLConnectionBackend()

    val asList: ResponseAs[List[String], Nothing] = asString.map(_.split(" ").toList)

    val resp = req.body("These are some wise words: use sttp").response(asList).send()
    println(resp.unsafeBody)
  }

  def serializingRequests(): Unit = {
    val req = sttp.get(uri"http://localhost:51823/echo/get")
    implicit val backend = HttpURLConnectionBackend()

    val listBody = List("These", "are", "some", "wise", "words")
    implicit val listSerializer: BodySerializer[List[String]] = l => StringBody(l.mkString("-"), "utf-8")

    val resp = req.body(listBody).send()
    println(resp.unsafeBody)
  }

  def errors(): Unit = {
    val req = sttp.get(uri"http://localhost:51823/notfound")
    implicit val backend = HttpURLConnectionBackend()

    val resp = req.send()
    println(resp.code)
    println(resp.body)
    println(resp.unsafeBody)
  }

  def circuitBreaker(): Unit = {
    class CircuitBreakerBackend[S](delegate: SttpBackend[Future, S], as: ActorSystem) extends SttpBackend[Future, S] {

      private val cb = new CircuitBreaker(as.scheduler,
        maxFailures = 3,
        callTimeout = 10.seconds,
        resetTimeout = 10.seconds)
        .onClose(println("CLOSE"))
        .onOpen(println("OPEN"))
        .onHalfOpen(println("HALF-OPEN"))

      override def send[T](request: Request[T, S]): Future[Response[T]] = {
        cb.withCircuitBreaker(delegate.send(request).map { r =>
          if (!r.isSuccess) throw new RuntimeException(s"Status code ${r.code}")
          r
        })
      }

      override def responseMonad: MonadError[Future] = delegate.responseMonad

      override def close: Unit = delegate.close()
    }

    val reqOk = sttp.get(uri"http://localhost:51823/echo/get")
    val reqError = sttp.get(uri"http://localhost:51823/404")

    val as = ActorSystem("example")
    implicit val backend = new CircuitBreakerBackend(AkkaHttpBackend.usingActorSystem(as), as)

    def runAndLog(r: Request[String, Nothing]): Unit = {
      println(Try(Await.result(r.send().map(_.unsafeBody), 60.seconds)))
    }

    runAndLog(reqError)
    runAndLog(reqError)
    runAndLog(reqError)
    runAndLog(reqError)
    runAndLog(reqError)
    runAndLog(reqError)
    runAndLog(reqError)
    runAndLog(reqError)
    while (true) {
      Thread.sleep(500L)
      runAndLog(reqOk)
    }
  }

  def testing(): Unit = {
    implicit val testingBackend = SttpBackendStub.synchronous
      .whenRequestMatches(_.uri.path.startsWith(List("hello", "world")))
      .thenRespond("Hello there!")
      .whenRequestMatches(_.method == Method.POST)
      .thenRespondServerError()

    val response1 = sttp.get(uri"http://example.org/hello/world/here").send()
    println(response1.body)

    val response2 = sttp.post(uri"http://example.org/d/e").send()
    println(response2.body)
  }

  testing()
}
