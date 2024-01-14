package org.ukrnastup.comments

import cats.effect.IO
import com.comcast.ip4s._
import logstage.LogIO
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router

object Server {
  def make(implicit logger: LogIO[IO]) = {

    val routes = HttpRoutes.of[IO] { case GET -> Root => // healthcheck
      logger.info("server: / called") *> Ok()
    }

    val httpApp = Router("/" -> routes).orNotFound

    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build
      .allocated
      .flatMap(_._2)
  }
}
