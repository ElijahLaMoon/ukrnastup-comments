package org.ukrnastup.comments

import java.time.ZonedDateTime

import BannedUser.*

final case class BannedUser(
    id: InnerId,
    isCurrentlyBanned: IsCurrentlyBanned,
    isChannel: IsChannel,
    telegramId: TelegramUserId,
    telegramName: TelegramName,
    telegramUsername: Option[TelegramUsername],
    reason: BanReason,
    bannedBy: BannedBy,
    bannedByTelegramId: BannedByTelegramUserId,
    messageGotBannedFor: Option[MessageGotBannedFor],
    messageGotBannedForLink: Option[MessageGotBannedForLink],
    createdAt: ZonedDateTime = now(),
    updatedAt: Option[ZonedDateTime] = None,
)

object BannedUser {
  final case class InnerId(value: Long)                  extends AnyVal
  final case class IsCurrentlyBanned(value: Boolean)     extends AnyVal
  final case class IsChannel(value: Boolean)             extends AnyVal
  final case class TelegramUserId(id: Long)              extends AnyVal
  final case class TelegramName(name: String)            extends AnyVal
  final case class TelegramUsername(username: String)    extends AnyVal
  final case class BanReason(text: String)               extends AnyVal
  final case class BannedBy(name: String)                extends AnyVal
  final case class BannedByTelegramUserId(id: Long)      extends AnyVal
  final case class MessageGotBannedFor(text: String)     extends AnyVal
  final case class MessageGotBannedForLink(link: String) extends AnyVal

  // TODO: sample data, to be removed
  lazy val bannedUsers = List(bannedUser1, bannedUser2)
  val bannedUser1 = BannedUser(
    InnerId(0L),
    IsCurrentlyBanned(true),
    IsChannel(false),
    TelegramUserId(1L),
    TelegramName("Alex"),
    Some(TelegramUsername("@alex")),
    BanReason("bad"),
    BannedBy("Vasya"),
    BannedByTelegramUserId(2L),
    Some(MessageGotBannedFor("some bad message #1")),
    None,
  )
  val bannedUser2 = bannedUser1.copy(
    InnerId(0L),
    IsCurrentlyBanned(true),
    IsChannel(false),
    TelegramUserId(2L),
    TelegramName("Vova"),
    Some(TelegramUsername("@vova")),
    BanReason("just because"),
    BannedBy("Vasya"),
    BannedByTelegramUserId(2L),
    Some(MessageGotBannedFor("some bad message #2")),
    None,
  )
}
