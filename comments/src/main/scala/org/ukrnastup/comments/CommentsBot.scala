package org.ukrnastup.comments

import buildinfo.BuildInfo
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.applicative.*
import cats.syntax.option.*
import iozhik.OpenEnum.Known
import logstage.LogIO
import org.ukrnastup.comments.Command as Cmd
import org.ukrnastup.comments.Database as Db
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

import java.time.format.DateTimeFormatter as DTF
import scala.concurrent.duration.DurationInt

import telegramium.bots.Chat
import telegramium.bots.User

class CommentsBot private (
    commentsChatId: Long,
    commentsLogsChannelId: Long,
    originalChannelId: Long,
)(using api: Api[IO], logger: LogIO[IO])
    extends LongPollBot[IO](api) {

  /** Launches bot in background with dropping pending Telegram updates,
    * refreshes admins cache
    */
  override def start(): IO[Unit] = (for
    _ <- handleUpdateAdminsCommand
    _ <- sendMessage(
      ChatIntId(commentsLogsChannelId),
      s"Бот версії ${BuildInfo.version} онлайн",
      disableNotification = true.some,
    ).exec
    _          <- api.execute(this.deleteWebhook(dropPendingUpdates = true.some))
    refCounter <- Ref[IO].of(0)
    offsetKeeper: LongPollBot.OffsetKeeper[IO] = new {
      override def getOffset              = refCounter.get
      override def setOffset(offset: Int) = refCounter.set(offset)
    }
    _ <- this.poll(offsetKeeper)
  yield ()).start.void

  def handleCommand(
      command: Command,
      message: Message,
  ): IO[String] =
    logger.info(s"handling $command with $message") *> {
      command match
        case Cmd.Ban(reason) =>
          handleBanCommand(reason, message)
        case Cmd.BanWithoutReason =>
          handleBanCommand("причину не вказано", message)
        case Cmd.Lookup(username) =>
          handleLookupCommand(username)
        case Cmd.UpdateAdmins =>
          handleUpdateAdminsCommand
    }

  private def handleBanCommand(
      reason: String,
      adminMessage: Message,
  ): IO[String] = {
    val replyCommandWasAppliedTo = adminMessage.replyToMessage
    val isBanAppliedWithoutAReply =
      replyCommandWasAppliedTo
        .map(_.senderChat.exists(_.id == originalChannelId))
        .getOrElse(false)

    if replyCommandWasAppliedTo.isEmpty then
      "використовуйте цю команду у відповідь на реплай людини, яку ви хочете забанити"
        .pure[IO]
    else if isBanAppliedWithoutAReply then
      logger.info(
        s"Admin is replying to ${replyCommandWasAppliedTo -> "this message"}"
      ) *>
        "ви намагаєтесь заблокувати канал, до якого прив'язаний цей чат з коментарями. швидше за все ви використали команду /ban у коментарях під постом, але забули додати реплай на повідомлення користувача, якого ви хочете заблокувати"
          .pure[IO]
    else
      for {
        isSentFromChat <- Ref[IO].of(false)
        messageToBanFor = replyCommandWasAppliedTo.get
        _ <- IO(messageToBanFor.senderChat.nonEmpty).ifM(
          isSentFromChat.set(true),
          /* otherwise leave untouched */ IO.unit,
        )

        idToBan = messageToBanFor
          .senderChat
          .map(_.id)
          .orElse(messageToBanFor.from.map(_.id))
          .get
        bannedBy = adminMessage
          .authorSignature
          .map(t => s"анонімний адмін '$t'")
          .orElse {
            adminMessage.from.map(_.toAdmin).map(_.telegramName.name)
          }
          .get
        bannedById = adminMessage
          .senderChat
          .map(_ => commentsChatId)
          .orElse(adminMessage.from.map(_.id))
          .get
        messageGotBannedForLink =
          s"https://t.me/c/${commentsChatId.toString.drop(4)}/${messageToBanFor.messageId}"

        wasUserPreviouslyBanned <- Db
          .getBannedUserByTelegramId(BannedUser.TelegramUserId(idToBan))
          .map(_.isDefined)

        convertChatOrUserToIoBannedUser = (_: Option[Chat | User])
          .get // safe
          .toBannedUser(
            banReason = reason,
            bannedBy = bannedBy,
            bannedByTelegramId = bannedById,
            messageGotBannedFor = messageToBanFor
              .text
              .getOrElse(
                "користувача було забанено не за текстове повідомлення"
              ),
            messageGotBannedForLink = messageGotBannedForLink,
            updatedAt = if wasUserPreviouslyBanned then now().some else none,
          )
          .pure[IO]

        bannedUserEntity <- isSentFromChat
          .get
          .ifM(
            ifTrue = convertChatOrUserToIoBannedUser(messageToBanFor.senderChat),
            ifFalse = convertChatOrUserToIoBannedUser(messageToBanFor.from),
          )

        // ban on Telegram
        _ <- isSentFromChat
          .get
          .ifM(
            ifTrue = logger.info(
              s"attempting to ban user (sent on behalf of chat) $bannedUserEntity"
            ) >>
              banChatSenderChat(
                ChatIntId(commentsChatId),
                bannedUserEntity.telegramId.id,
              ).exec,
            ifFalse = logger.info(
              s"attempting to ban regular user $bannedUserEntity"
            ) >> banChatMember(
              ChatIntId(commentsChatId),
              bannedUserEntity.telegramId.id,
            ).exec,
          )
        // record ban in db
        _ <- Db.insertOrUpdateBannedUser(bannedUserEntity)

        // logging
        logMessage <- sendToLogsChannel {
          import bannedUserEntity.telegramName.name as tgName
          import bannedUserEntity.telegramUsername as tgUsername
          import bannedUserEntity.reason.text
          import bannedUserEntity.bannedBy.name as adminName
          import bannedUserEntity.messageGotBannedFor as msgBannedFor
          import bannedUserEntity.messageGotBannedForLink as msgLink

          val user = tgUsername.fold(tgName)(tu => s"$tgName ${tu.username}")

          s"""
             |$adminName блокує користувача $user
             |причина: $text
             |бан за повідомлення: ${msgBannedFor.map(_.text).getOrElse("не вказано")}
             |посилання: ${msgLink.map(_.link).getOrElse("відсутнє")}
             |""".stripMargin
        }
        _ <- logger.info(s"sent $logMessage to logs channel")
      } yield "користувача було успішно заблоковано"
  }

  private def handleLookupCommand(username: String): IO[String] =
    for maybeBannedUser <- Db.getBannedUserByUsername(BannedUser.TelegramUsername(username))
    yield
      if maybeBannedUser.isEmpty then s"@$username не в бані"
      else {
        val bu   = maybeBannedUser.head
        val zdt  = bu.updatedAt.fold(bu.createdAt)(identity)
        val date = zdt.format(DTF.ofPattern("dd.MM.YYYY"))
        val time = zdt.format(DTF.ofPattern("HH:mm"))
        s"@$username було заблоковано $date о $time: ${bu.reason}"
      }

  private def handleUpdateAdminsCommand: IO[String] = (for
    dbAdmins <- Db.getAdmins
    tgAdmins <- getChatAdministrators(ChatIntId(commentsChatId)).exec
  yield {
    val dbAdminsIds = dbAdmins.map(_.telegramId.id).toSet
    val tgAdminsAsUsers = tgAdmins.collect {
      case Known(a: ChatMemberAdministrator) => a.user
      case Known(o: ChatMemberOwner)         => o.user
    }
    val tgAdminsIds = tgAdminsAsUsers.map(_.id).toSet
    if (dbAdminsIds == tgAdminsIds)
      logger.info("адміни не змінилися") >>
        "адміни не змінилися, нічого не зроблено".pure[IO]
    else {
      val newAdmins =
        tgAdminsAsUsers
          .filter(tga => !dbAdminsIds.contains(tga.id))
          .map(_.toAdmin)
      val adminsToDelete =
        dbAdmins.filter(dba => !tgAdminsIds.contains(dba.telegramId.id))

      val idLink = (id: Long) => s"tg://user?id=$id"
      val charsToPrecedeWithSlash = List(
        '_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!',
        '@', // @ added by me
      )
      val withPrecededSlashes: String => String =
        _.map(c => if charsToPrecedeWithSlash contains c then s"\\$c" else c.toString).mkString

      val adminToLogString = (a: Admin) =>
        a.telegramUsername
          .map(_.username)
          .fold(ifEmpty =
            s"[${a.telegramName.name}](${idLink(a.telegramId.id)})" // markdown link
          )(username => withPrecededSlashes(s"${a.telegramName.name} @$username"))

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

      for
        _ <- ifOrUnit(newAdmins.nonEmpty) {
          Db.insertAdmins(newAdmins) >>
            sendToLogsChannel(newAdminsAsLog, Markdown2.some)
        }
        _ <- ifOrUnit(adminsToDelete.nonEmpty) {
          Db.deleteAdmins(adminsToDelete) >>
            sendToLogsChannel(adminsToDeleteAsLog, Markdown2.some)
        }
        _ <- log
      yield "оновлено список адмінів"
    }
  }).flatten

  private def sendToLogsChannel(
      message: String,
      parseMode: Option[ParseMode] = None,
  ) = sendMessage(
    ChatIntId(commentsLogsChannelId),
    message,
    parseMode = parseMode,
  ).exec
}

object CommentsBot {
  def make(
      commentsChatId: Long,
      commentsLogsChannelId: Long,
      originalChannelId: Long,
  )(using api: Api[IO], logger: LogIO[IO]) =
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
                      replyParameters = Some(ReplyParameters(msg.messageId)),
                    ).exec
                    messagesToDelete = List(
                      msg.messageId,
                      botsReplyMessage.messageId,
                    )
                    _ <- IO.sleep(2.seconds)
                    deleteResult <- deleteMessages(
                      ChatIntId(commentsChatId),
                      messagesToDelete,
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

}
