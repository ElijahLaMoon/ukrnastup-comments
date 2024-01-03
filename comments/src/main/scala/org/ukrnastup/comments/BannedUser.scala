package org.ukrnastup.comments

import BannedUser._
import telegramium.bots.ChatId
import java.time.ZonedDateTime
import java.time.ZoneId

final case class BannedUser(
    id: InnerId,
    telegramId: TelegramUserId,
    telegramName: Option[TelegramName],
    telegramUsername: Option[TelegramUsername],
    reason: Option[BanReason],
    bannedBy: Option[BannedBy],
    bannedById: BannedByTelegramUserId,
    messageGotBannedFor: Option[MessageGotBannedFor],
    messageGotBannedForLink: Option[MessageGotBannedForLink],
    createdAt: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Europe/Kyiv")),
    updatedAt: Option[ZonedDateTime] = None
)

object BannedUser {
  final case class InnerId(value: Long) extends AnyVal
  final case class TelegramUserId(id: ChatId) extends AnyVal
  final case class TelegramName(name: String) extends AnyVal
  final case class TelegramUsername(username: String) extends AnyVal
  final case class BanReason(text: String) extends AnyVal
  final case class BannedBy(name: String) extends AnyVal
  final case class BannedByTelegramUserId(id: ChatId) extends AnyVal
  final case class MessageGotBannedFor(text: String) extends AnyVal
  final case class MessageGotBannedForLink(link: String)
      extends AnyVal // TODO: better type than String?

  // TODO: sample data, to be removed
  import telegramium.bots.ChatIntId
  lazy val bannedUsers = List(bannedUser1, bannedUser2)
  val bannedUser1 = BannedUser(
    InnerId(1),
    TelegramUserId(ChatIntId(1)),
    Some(TelegramName("Alex")),
    Some(TelegramUsername("@alex")),
    Some(BanReason("bad")),
    Some(BannedBy("Vasya")),
    BannedByTelegramUserId(ChatIntId(2)),
    Some(MessageGotBannedFor("some bad message #1")),
    None
  )
  val bannedUser2 = bannedUser1.copy(
    InnerId(2),
    TelegramUserId(ChatIntId(2)),
    Some(TelegramName("Vova")),
    Some(TelegramUsername("@vova")),
    Some(BanReason("just because")),
    Some(BannedBy("Vasya")),
    BannedByTelegramUserId(ChatIntId(2)),
    Some(MessageGotBannedFor("some bad message #2")),
    None
  )
}
