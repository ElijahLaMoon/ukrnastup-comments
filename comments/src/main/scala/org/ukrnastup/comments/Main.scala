package org.ukrnastup.comments

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import com.typesafe.config.ConfigFactory
import izumi.logstage.api.IzLogger
import izumi.logstage.api.routing.StaticLogRouter
import logstage.LogIO
import org.http4s.ember.client.EmberClientBuilder
import telegramium.bots.BotCommand
import telegramium.bots.high._
import telegramium.bots.high.implicits.methodOps

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    val _logger = IzLogger(threshold = logstage.Log.Level.Info)
    implicit val logger = LogIO.fromLogger[IO](_logger)
    StaticLogRouter.instance.setup(_logger.router) // bind slf4j to logstage

    val cfg = ConfigFactory.defaultApplication().resolve()
    val token = cfg.getString("bot.token")
    val commentsChatId = cfg.getLong("bot.commentsChatId")
    val commentsLogsChannelId = cfg.getLong("bot.commentsLogsChannelId")

    EmberClientBuilder
      .default[IO]
      .build
      .both(Database.migrateDb)
      .use { case (httpClient, _) =>
        implicit val api: Api[IO] =
          BotApi(httpClient, baseUrl = s"https://api.telegram.org/bot$token")
        val bot =
          CommentsBot.make(commentsChatId, commentsLogsChannelId)
        val commands =
          Command.visible.map(c => BotCommand(c.command, c.description))

        for {
          _ <- bot.setMyCommands(commands).exec
          _ <- bot.start()
          _ <- logger.info("Bot started")
          ec <- Server.make.useForever.as(ExitCode.Success)
        } yield ec
      }
  }
}
