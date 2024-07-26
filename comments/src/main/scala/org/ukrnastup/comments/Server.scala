package org.ukrnastup.comments

import cats.effect.IO
import com.comcast.ip4s.*
import logstage.LogIO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router

object Server {
  def make(using logger: LogIO[IO]) = {

    val routes = HttpRoutes.of[IO] { case GET -> Root =>
      logger.info("server: / called") *> Ok("Healthcheck passed")
    }

    val httpApp = Router("/" -> routes).orNotFound

    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"9000")
      .withHttpApp(httpApp)
      .build
  }
}
