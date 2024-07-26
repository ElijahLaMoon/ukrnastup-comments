package org.ukrnastup.comments

import cats.effect.IO
import cats.effect.kernel.Resource
import com.typesafe.config.ConfigFactory
import doobie.Transactor
import doobie.free.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits.toConnectionIOOps
import doobie.util.ExecutionContexts
import fly4s.Fly4s
import fly4s.data.Fly4sConfig
import fly4s.data.Location
import io.getquill.*
import io.getquill.doobie.DoobieContext

object Database extends DoobieContext.SQLite(SnakeCase) {
  private val cfg = ConfigFactory.defaultApplication()
  private val driver = cfg.getString("ctx.driverClassName")
  private val url = cfg.getString("ctx.jdbcUrl")

  inline given insertMetaBannedUsers: InsertMeta[BannedUser] =
    insertMeta(_.id)
  inline given insertMetaAdmins: InsertMeta[Admin] =
    insertMeta(_.id)

  inline def bannedUsers = quote(querySchema[BannedUser]("banned_users"))
  inline def admins = quote(querySchema[Admin]("admins_cache"))

  private val transactor: Resource[IO, Transactor[IO]] = for {
    ec <- ExecutionContexts.fixedThreadPool[IO](5)
    xa <- HikariTransactor.newHikariTransactor[IO](
      driverClassName = driver,
      url = url,
      user = "",
      pass = "",
      ec
    )
  } yield xa

  val migrateDb = Fly4s
    .make[IO](
      url = url,
      user = None,
      password = None,
      config = Fly4sConfig(
        locations = List(Location("migrations"))
      )
    )
    .evalMap(_.migrate)

  def processAction[A](action: ConnectionIO[A]): IO[A] =
    transactor.use { xa =>
      action.transact(xa)
    }

  // ------------- USERS TABLE METHODS -------------
  def getBannedUsers(
      pred: BannedUser => Boolean = _ => true
  ): IO[List[BannedUser]] =
    processAction(
      stream(bannedUsers).filter(pred).compile.toList
    )
  def insertOrUpdateBannedUser(user: BannedUser): IO[Long] =
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
  def getBannedUserByTelegramId(
      telegramId: BannedUser.TelegramUserId
  ): IO[List[BannedUser]] =
    processAction(
      run(
        bannedUsers.filter(_.telegramId == lift(telegramId))
      )
    )
  def getBannedUserByUsername(
      username: BannedUser.TelegramUsername
      // ): IO[List[BannedUser.TelegramUsername]] =
  ): IO[List[BannedUser]] =
    processAction(
      run(
        bannedUsers
          .filter { u =>
            u.telegramUsername.isDefined &&
            u.telegramUsername.exists(_ == lift(username))
          }
      )
    )
  def deleteBannedUser(telegramId: BannedUser.TelegramUserId): IO[Long] =
    processAction(
      run(
        bannedUsers.filter(_.telegramId == lift(telegramId)).delete
      )
    )
  def updateBannedUser(user: BannedUser): IO[Long] =
    processAction(
      run(
        bannedUsers
          .filter(_.telegramId == lift(user.telegramId))
          .updateValue(lift(user))
      )
    )
  // ------------- ADMINS TABLE METHODS -------------
  val getAdmins: IO[List[Admin]] =
    processAction(
      stream(admins).compile.toList
    )
  def insertAdmin(admin: Admin): IO[Long] =
    processAction(
      run(
        admins.insertValue(lift(admin))
      )
    )
  def insertAdmins(adminsList: List[Admin]): IO[List[Long]] =
    processAction(
      run(
        liftQuery(adminsList).foreach(admins.insertValue(_))
      )
    )
  def getAdminByTelegramId(telegramId: Admin.TelegramUserId): IO[List[Admin]] =
    processAction(
      run(
        admins.filter(_.telegramId == lift(telegramId))
      )
    )
  def deleteAdmin(telegramId: Admin.TelegramUserId): IO[Long] =
    processAction(
      run(
        admins.filter(_.telegramId == lift(telegramId)).delete
      )
    )
  def deleteAdmins(adminsList: List[Admin]): IO[List[Long]] =
    processAction(
      run(
        liftQuery(adminsList).foreach(adminToDelete =>
          admins.filter(_.telegramId == adminToDelete.telegramId).delete
        )
      )
    )
  def updateAdmin(admin: Admin): IO[Long] =
    processAction(
      run(
        admins
          .filter(_.telegramId == lift(admin.telegramId))
          .updateValue(lift(admin))
      )
    )
}
