package org.ukrnastup.comments

import java.time.ZonedDateTime

import Admin.*

final case class Admin(
    id: InnerId,
    telegramId: TelegramUserId,
    telegramName: TelegramName,
    telegramUsername: Option[TelegramUsername],
    createdAt: ZonedDateTime = now(),
)

object Admin {
  final case class InnerId(value: Long)               extends AnyVal
  final case class TelegramUserId(id: Long)           extends AnyVal
  final case class TelegramName(name: String)         extends AnyVal
  final case class TelegramUsername(username: String) extends AnyVal
}
