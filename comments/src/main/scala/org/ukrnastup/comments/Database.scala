package org.ukrnastup.comments

import cats.effect.kernel.Resource
import cats.{effect => ce}
import com.typesafe.config.ConfigFactory
import doobie.Transactor
import doobie.hikari.HikariTransactor
import doobie.implicits.toConnectionIOOps
import doobie.util.ExecutionContexts
import fly4s.Fly4s
import fly4s.data.Fly4sConfig
import fly4s.data.Location

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

  @nowarn implicit val insertMetaBannedUsers =
    insertMeta[BannedUser](_.id)
  @nowarn implicit val insertMetaAdmins =
    insertMeta[Admin](_.id)

  val bannedUsers = quote(querySchema[BannedUser]("banned_users"))
  val admins = quote(querySchema[Admin]("admins_cache"))
}
