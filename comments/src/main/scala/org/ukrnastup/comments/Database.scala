package org.ukrnastup.comments

import doobie.Transactor
import doobie.implicits.toConnectionIOOps
import doobie.hikari.HikariTransactor
import cats.effect.kernel.Resource
import cats.{effect => ce}
import doobie.util.ExecutionContexts
import com.typesafe.config.ConfigFactory
import telegramium.bots.ChatId
import telegramium.bots.ChatIntId
import telegramium.bots.ChatStrId
import scala.annotation.nowarn

object Database {
  private val cfg = ConfigFactory.defaultApplication()
  private val driver = cfg.getString("ctx.driverClassName")
  private val url = cfg.getString("ctx.jdbcUrl")

  private val transactor: Resource[ce.IO, Transactor[ce.IO]] = for {
    ec <- ExecutionContexts.fixedThreadPool[ce.IO](5)
    xa <- HikariTransactor.newHikariTransactor[ce.IO](
      driverClassName = driver,
      url = url,
      user = "",
      pass = "",
      ec
    )
  } yield xa

  import fly4s.core.Fly4s
  import fly4s.core.data.Fly4sConfig
  import fly4s.core.data.Location
  val migrateDb = Fly4s
    .make[ce.IO](
      url = url,
      user = None,
      password = None,
      config = Fly4sConfig(
        locations = List(Location("migrations"))
      )
    )
    .evalMap(_.migrate)

  def processAction[A](action: doobie.ConnectionIO[A]): ce.IO[A] =
    transactor.use { xa =>
      action.transact(xa)
    }

  import schema._
  import schema.ctx._
  val getBannedUsers = processAction(stream(bannedUsers).compile.toList)
  def insertBannedUser(user: BannedUser) =
    processAction {
      run(
        bannedUsers.insertValue(lift(user))
      )
    }
  def getBannedUserByTelegramId(telegramId: BannedUser.TelegramUserId) =
    processAction(
      run(
        bannedUsers.filter(_.telegramId == lift(telegramId))
      )
    )
  def deleteBannedUser(telegramId: BannedUser.TelegramUserId) =
    processAction(
      run(
        bannedUsers.filter(_.telegramId == lift(telegramId)).delete
      )
    )
  def updateBannedUser(user: BannedUser) =
    processAction(
      run(
        bannedUsers
          .filter(_.telegramId == lift(user.telegramId))
          .updateValue(lift(user))
      )
    )
}

object schema {
  import io.getquill.SnakeCase
  import io.getquill.doobie.DoobieContext

  val ctx = new DoobieContext.SQLite(SnakeCase)
  import ctx._

  @nowarn implicit val chatIdEncoder =
    MappedEncoding[ChatId, Long] {
      case ChatIntId(id) => id
      case ChatStrId(id) =>
        scala.util
          .Try(id.toLong)
          .getOrElse(
            0L
          ) // TODO: add logging probably, this path shouldn't be reached
    }
  @nowarn implicit val chatIdDecoder =
    MappedEncoding[Long, ChatId](ChatIntId(_))
  @nowarn implicit val insertMetaBannedUsers =
    insertMeta[BannedUser](_.id)

  val bannedUsers = quote {
    querySchema[BannedUser](
      "banned_users",
      _.id -> "id",
      _.telegramId -> "telegram_id",
      _.telegramName -> "telegram_name",
      _.telegramUsername -> "telegram_username",
      _.reason -> "reason",
      _.bannedBy -> "banned_by",
      _.bannedById -> "banned_by_id",
      _.messageGotBannedFor -> "message_got_banned_for",
      _.messageGotBannedForLink -> "message_got_banned_for_link",
      _.createdAt -> "created_at",
      _.updatedAt -> "updated_at"
    )
  }
}
