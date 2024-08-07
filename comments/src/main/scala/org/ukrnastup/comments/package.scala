package org.ukrnastup

import cats.syntax.option.*
import telegramium.bots.Chat
import telegramium.bots.User

import java.time.ZoneId
import java.time.ZonedDateTime

package object comments {

  /** Current time by Kyiv
    */
  def now(): ZonedDateTime = ZonedDateTime.now(ZoneId.of("Europe/Kyiv"))

  object Extensions { // my custom extensions

    implicit class UserToAdmin(private val user: User) {
      import Admin._
      def toAdmin: Admin = Admin(
        id = InnerId(0L), // the value is generated by db on insertion
        telegramId = TelegramUserId(user.id),
        telegramName =
          user.lastName.fold(TelegramName(user.firstName))(ln => TelegramName(s"${user.firstName} $ln")),
        telegramUsername = user.username.map(TelegramUsername(_)),
      )
    }

    implicit class UserToBannedUser(private val user: User) {
      import BannedUser._
      def toBannedUser(
          banReason: String,
          bannedBy: String,
          bannedByTelegramId: Long,
          messageGotBannedFor: String,
          messageGotBannedForLink: String,
          updatedAt: Option[ZonedDateTime] = None,
      ): BannedUser = BannedUser(
        id = InnerId(0L), // the value is generated by db on insertion
        isCurrentlyBanned = IsCurrentlyBanned(true),
        isChannel = IsChannel(false),
        telegramId = TelegramUserId(user.id),
        telegramName =
          user.lastName.fold(TelegramName(user.firstName))(ln => TelegramName(s"${user.firstName} $ln")),
        telegramUsername = user.username.map(TelegramUsername(_)),
        reason = BanReason(banReason),
        bannedBy = BannedBy(bannedBy),
        bannedByTelegramId = BannedByTelegramUserId(bannedByTelegramId),
        messageGotBannedFor = MessageGotBannedFor(messageGotBannedFor).some,
        messageGotBannedForLink = MessageGotBannedForLink(messageGotBannedForLink).some,
        updatedAt = updatedAt,
      )
    }

    implicit class ChatToBannedUser(private val chat: Chat) {
      import BannedUser._
      def toBannedChat(
          banReason: String,
          bannedBy: String,
          bannedByTelegramId: Long,
          messageGotBannedFor: String,
          messageGotBannedForLink: String,
          updatedAt: Option[ZonedDateTime] = None,
      ): BannedUser = BannedUser(
        id = InnerId(0L), // the value is generated by db on insertion
        isCurrentlyBanned = IsCurrentlyBanned(true),
        isChannel = IsChannel(true),
        telegramId = TelegramUserId(chat.id),
        telegramName = chat
          .title
          .map(TelegramName(_))
          .get, // since this method is intended for chats this should never fail
        telegramUsername = none,
        reason = BanReason(banReason),
        bannedBy = BannedBy(bannedBy),
        bannedByTelegramId = BannedByTelegramUserId(bannedByTelegramId),
        messageGotBannedFor = MessageGotBannedFor(messageGotBannedFor).some,
        messageGotBannedForLink = MessageGotBannedForLink(messageGotBannedForLink).some,
        updatedAt = updatedAt,
      )
    }
  }
}
