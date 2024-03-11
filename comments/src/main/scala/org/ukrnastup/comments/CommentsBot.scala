package org.ukrnastup.comments

import buildinfo.BuildInfo
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.applicative._
import cats.syntax.option._
import logstage.LogIO
import org.ukrnastup.comments.{Command => Cmd}
import org.ukrnastup.comments.{Database => Db}
import telegramium.bots.ChatIntId
import telegramium.bots.ChatMemberAdministrator
import telegramium.bots.ChatMemberOwner
import telegramium.bots.Markdown2
import telegramium.bots.Message
import telegramium.bots.ParseMode
import telegramium.bots.ReplyParameters
import telegramium.bots.high.Api
import telegramium.bots.high.LongPollBot
import telegramium.bots.high.implicits.methodOps

import java.time.format.{DateTimeFormatter => DTF}
import scala.concurrent.duration.DurationInt

import Extensions._

class CommentsBot private (
    commentsChatId: Long,
    commentsLogsChannelId: Long,
    originalChannelId: Long
)(implicit
    api: Api[IO],
    logger: LogIO[IO]
) extends LongPollBot[IO](api) {

  /** Launches bot in background with dropping pending Telegram updates,
    * refreshes admins cache
    */
  override def start(): IO[Unit] = (for {
    _ <- handleUpdateAdminsCommand
    _ <- sendMessage(
      ChatIntId(commentsLogsChannelId),
      s"Бот версії ${BuildInfo.version} онлайн",
      disableNotification = true.some
    ).exec
    _ <- api.execute(this.deleteWebhook(dropPendingUpdates = true.some))
    refCounter <- Ref[IO].of(0)
    offsetKeeper = new LongPollBot.OffsetKeeper[IO] {
      def getOffset = refCounter.get
      def setOffset(offset: Int) = refCounter.set(offset)
    }
    _ <- this.poll(offsetKeeper)
  } yield ()).start.void

  def handleCommand(
      command: Command,
      message: Message
  ): IO[String] =
    logger.info(s"handling $command with $message") *> {
      command match {
        case Cmd.Ban(reason) =>
          handleBanCommand(reason, message)
        case Cmd.BanWithoutReason =>
          handleBanCommand("причину не вказано", message)
        case Cmd.Lookup(username) =>
          handleLookupCommand(username)
        case Cmd.UpdateAdmins =>
          handleUpdateAdminsCommand
      }
    }

  private def handleBanCommand(
      reason: String,
      adminMessage: Message
  ): IO[String] = {
    val replyCommandWasAppliedTo = adminMessage.replyToMessage

    if (replyCommandWasAppliedTo.isEmpty)
      "використовуйте цю команду у відповідь на реплай людини, яку ви хочете забанити"
        .pure[IO]
    else if (replyCommandWasAppliedTo.get.chat.id == commentsChatId)
      "ви намагаєтесь заблокувати канал, до якого прив'язаний цей чат з коментарями. швидше за все ви використали команду /ban у коментарях під постом, але забули додати реплай на повідомлення користувача, якого ви хочете заблокувати"
        .pure[IO]
    else
      for {
        isSentFromChat <- Ref[IO].of(false)
        messageToBanFor = replyCommandWasAppliedTo.get
        _ <- IO(messageToBanFor.senderChat.nonEmpty).ifM(
          isSentFromChat.set(true),
          /* otherwise leave untouched */ IO.unit
        )

        idToBan = messageToBanFor.senderChat
          .map(_.id)
          .orElse(messageToBanFor.from.map(_.id))
          .get
        bannedBy = adminMessage.authorSignature
          .map(t => s"анонімний адмін '$t'")
          .orElse {
            adminMessage.from.map(_.toAdmin).map(_.telegramName.name)
          }
          .get
        bannedById = adminMessage.senderChat
          .map(_ => commentsChatId)
          .orElse(adminMessage.from.map(_.id))
          .get
        messageGotBannedForLink =
          s"https://t.me/c/${commentsChatId.toString.drop(4)}/${messageToBanFor.messageId}"

        maybePreviouslyBannedUser: Option[BannedUser] <- Db
          .getBannedUserByTelegramId(BannedUser.TelegramUserId(idToBan))
          .map(_.headOption)
        wasPreviouslyBanned = maybePreviouslyBannedUser.isDefined

        genericParametersOfBan = (
          reason,
          bannedBy,
          bannedById,
          messageToBanFor.text.getOrElse(
            "користувача було забанено не за текстове повідомлення"
          ),
          messageGotBannedForLink,
          if (wasPreviouslyBanned) now().some else none
        )

        // Metals fails to infer type for whatever reason
        userReadyToBan: BannedUser <- isSentFromChat.get
          .ifM(
            ifTrue = (messageToBanFor.senderChat.get.toBannedChat _)
              .tupled(genericParametersOfBan)
              .pure[IO],
            ifFalse = (messageToBanFor.from.get.toBannedUser _)
              .tupled(genericParametersOfBan)
              .pure[IO]
          )

        // ban on Telegram
        _ <- isSentFromChat.get.ifM(
          ifTrue = logger.info(
            s"attempting to ban user (sent on behalf of chat) $userReadyToBan"
          ) >>
            banChatSenderChat(
              ChatIntId(commentsChatId),
              userReadyToBan.telegramId.id
            ).exec,
          ifFalse = logger.info(
            s"attempting to ban regular user $userReadyToBan"
          ) >> banChatMember(
            ChatIntId(commentsChatId),
            userReadyToBan.telegramId.id
          ).exec
        )
        // ban in db
        _ <- Db.insertOrUpdateBannedUser(userReadyToBan)

        // logging
        logMessage <- sendToLogsChannel {
          import userReadyToBan.telegramName.{name => tgName}
          import userReadyToBan.{telegramUsername => tgUsername}
          import userReadyToBan.reason.text
          import userReadyToBan.bannedBy.{name => adminName}
          import userReadyToBan.{messageGotBannedFor => msgBannedFor}
          import userReadyToBan.{messageGotBannedForLink => msgLink}

          val user = tgUsername.fold(tgName)(tu => s"${tgName} ${tu.username}")

          s"""
          |$adminName блокує користувача $user
          |причина: $text
          |бан за повідомлення: ${msgBannedFor
              .map(_.text)
              .getOrElse("не вказано")}
          |посилання: ${msgLink.map(_.link).getOrElse("відсутнє")}
          |""".stripMargin
        }
        _ <- logger.info(s"sent $logMessage to logs channel")
      } yield "користувача було успішно заблоковано"
  }

  private def handleLookupCommand(username: String): IO[String] = for {
    bannedUserList <- Db.getBannedUserByUsername(
      BannedUser.TelegramUsername(username)
    )
  } yield {
    if (bannedUserList.isEmpty) s"@$username не в бані"
    else {
      val bu = bannedUserList.head
      val zdt = bu.updatedAt.fold(bu.createdAt)(identity)
      val date = zdt.format(DTF.ofPattern("dd.MM.YYYY"))
      val time = zdt.format(DTF.ofPattern("HH:mm"))
      s"@$username було заблоковано $date о $time: ${bu.reason}"
    }
  }

  private def handleUpdateAdminsCommand: IO[String] = (for {
    dbAdmins <- Db.getAdmins
    tgAdmins <- getChatAdministrators(ChatIntId(commentsChatId)).exec
  } yield {
    val dbAdminsIds = dbAdmins.map(_.telegramId.id).toSet
    val tgAdminsAsUsers = tgAdmins.collect {
      case a: ChatMemberAdministrator => a.user
      case o: ChatMemberOwner         => o.user
    }
    val tgAdminsIds = tgAdminsAsUsers.map(_.id).toSet
    if (dbAdminsIds == tgAdminsIds) {
      logger.info("адміни не змінилися") >>
        "адміни не змінилися, нічого не зроблено".pure[IO]
    } else {
      val newAdmins =
        tgAdminsAsUsers
          .filter(tga => !dbAdminsIds.contains(tga.id))
          .map(_.toAdmin)
      val adminsToDelete =
        dbAdmins.filter(dba => !tgAdminsIds.contains(dba.telegramId.id))

      val idLink = (id: Long) => s"tg://user?id=$id"
      val charsToPrecedeWithSlash = List(
        '_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|',
        '{', '}', '.', '!', '@' // @ added by me
      )
      val withPrecededSlashes: String => String = _.map(c =>
        if (charsToPrecedeWithSlash.contains(c)) s"\\$c" else c.toString
      ).mkString

      val adminToLogString = (a: Admin) =>
        a.telegramUsername
          .map(_.username)
          .fold(
            s"[${a.telegramName.name}](${idLink(a.telegramId.id)})"
          )(username =>
            withPrecededSlashes { s"${a.telegramName.name} @${username}" }
          )

      lazy val newAdminsAsLog =
        s"додано в кеш адмінів: ${newAdmins.map(adminToLogString).mkString(", ")}"
      lazy val adminsToDeleteAsLog =
        s"видалено з кешу адмінів: ${adminsToDelete.map(adminToLogString).mkString(", ")}"
      lazy val allAdminsAsLog =
        s"$newAdminsAsLog; $adminsToDeleteAsLog"

      val log = (newAdmins.isEmpty, adminsToDelete.isEmpty) match {
        case (true, true) =>
          logger.info(
            "адміни не змінилися"
          )
        case (false, true) =>
          logger.info(
            s"${newAdminsAsLog -> "" -> null}"
          )
        case (true, false) =>
          logger.info(
            s"${adminsToDeleteAsLog -> "" -> null}"
          )
        case (false, false) =>
          logger.info(
            s"${allAdminsAsLog -> "" -> null}"
          )
      }

      for {
        _ <-
          if (newAdmins.nonEmpty)
            Db.insertAdmins(newAdmins) >>
              sendToLogsChannel(newAdminsAsLog, Markdown2.some)
          else IO.unit
        _ <-
          if (adminsToDelete.nonEmpty)
            Db.deleteAdmins(adminsToDelete) >>
              sendToLogsChannel(adminsToDeleteAsLog, Markdown2.some)
          else IO.unit
        _ <- log
      } yield "оновлено список адмінів"
    }
  }).flatten

  private def sendToLogsChannel(
      message: String,
      parseMode: Option[ParseMode] = None
  ) = sendMessage(
    ChatIntId(commentsLogsChannelId),
    message,
    parseMode = parseMode
  ).exec
}

object CommentsBot {
  def make(
      commentsChatId: Long,
      commentsLogsChannelId: Long,
      originalChannelId: Long
  )(implicit
      api: Api[IO],
      logger: LogIO[IO]
  ) =
    new CommentsBot(commentsChatId, commentsLogsChannelId, originalChannelId) {
      override def onMessage(msg: Message): IO[Unit] =
        // TODO: maybe add ability to ban by sending "/ban @username" in log channel?
        ifOrUnit(msg.chat.id == commentsChatId && msg.text.nonEmpty) {
          val command = Command.parse(msg.text.get)

          ifOrUnit(command.nonEmpty) {
            for {
              adminsIds <- Db.getAdmins.map(_.map(_.telegramId.id))

              _ <-
                ifOrUnit(
                  msg.from.fold(false)(u => adminsIds.contains(u.id)) ||
                    msg.senderChat.fold(false)(_.id == commentsChatId)
                ) {
                  for {
                    commandResult <- handleCommand(command.get, msg)
                    botsReplyMessage <- sendMessage(
                      ChatIntId(commentsChatId),
                      commandResult,
                      replyParameters = Some(ReplyParameters(msg.messageId))
                    ).exec
                    messagesToDelete = List(
                      msg.messageId,
                      botsReplyMessage.messageId
                    )
                    _ <- IO.sleep(2.seconds)
                    deleteResult <- deleteMessages(
                      ChatIntId(commentsChatId),
                      messagesToDelete
                    ).exec
                    _ <- logger.info(
                      s"Attempted to delete ${messagesToDelete -> "messages"} $deleteResult"
                    )
                  } yield ()
                }

            } yield ()
          }
        }
    }

  // little helper to avoid too many `IO.unit`s
  private def ifOrUnit[A](predicate: Boolean)(thunk: => IO[A]) =
    if (predicate) thunk.void
    else IO.unit
}
