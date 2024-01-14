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
  // ------------- USERS TABLE METHODS -------------
  def getBannedUsers(pred: BannedUser => Boolean = _ => true) =
    processAction(
      stream(bannedUsers).filter(pred).compile.toList
    )
  def insertOrUpdateBannedUser(user: BannedUser) =
    processAction(
      run(
        bannedUsers
          .insertValue(lift(user))
          .onConflictUpdate(_.telegramId)(
            (t, e) => t.isCurrentlyBanned -> e.isCurrentlyBanned,
            (t, e) => t.telegramName -> e.telegramName,
            (t, e) => t.telegramUsername -> e.telegramUsername,
            (t, e) => t.reason -> e.reason,
            (t, e) => t.bannedBy -> e.bannedBy,
            (t, e) => t.bannedByTelegramId -> e.bannedByTelegramId,
            (t, e) => t.messageGotBannedFor -> e.messageGotBannedFor,
            (t, e) => t.messageGotBannedForLink -> e.messageGotBannedForLink,
            (t, e) => t.updatedAt -> e.updatedAt
          )
      )
    )
  def getBannedUserByTelegramId(telegramId: BannedUser.TelegramUserId) =
    processAction(
      run(
        bannedUsers.filter(_.telegramId == lift(telegramId))
      )
    )
  def getBannedUserByUsername(username: BannedUser.TelegramUsername) =
    processAction(
      run(
        bannedUsers
          .filter(u =>
            u.telegramUsername.isDefined && // guarantees that username is not null
              u.telegramUsername.getOrNull == lift(username)
          )
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
  // ------------- ADMINS TABLE METHODS -------------
  val getAdmins =
    processAction(
      stream(admins).compile.toList
    )
  def insertAdmin(admin: Admin) =
    processAction(
      run(
        admins.insertValue(lift(admin))
      )
    )
  def insertAdmins(adminsList: List[Admin]) =
    processAction(
      run(
        liftQuery(adminsList).foreach(admins.insertValue(_))
      )
    )
  def getAdminByTelegramId(telegramId: Admin.TelegramUserId) =
    processAction(
      run(
        admins.filter(_.telegramId == lift(telegramId))
      )
    )
  def deleteAdmin(telegramId: Admin.TelegramUserId) =
    processAction(
      run(
        admins.filter(_.telegramId == lift(telegramId)).delete
      )
    )
  def deleteAdmins(adminsList: List[Admin]) =
    processAction(
      run(
        liftQuery(adminsList).foreach(adminToDelete =>
          admins.filter(_.telegramId == adminToDelete.telegramId).delete
        )
      )
    )
  def updateAdmin(admin: Admin) =
    processAction(
      run(
        admins
          .filter(_.telegramId == lift(admin.telegramId))
          .updateValue(lift(admin))
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
