package org.ukrnastup.comments

import cats.syntax.option.*

sealed abstract class Command(val command: String, val description: String)

object Command {
  val parse: String => Option[Command] = _.strip() match {
    case s"/ban@${_} $reason" => Ban(reason).some
    case s"/ban $reason"      => Ban(reason).some

    case s"/ban@${_}" => BanWithoutReason.some
    case "/ban"       => BanWithoutReason.some

    case s"/lookup@${_} $username" => Lookup(username).some
    case s"/lookup @$username"     => Lookup(username).some

    case "/update_admins"       => UpdateAdmins.some
    case s"/update_admins@${_}" => UpdateAdmins.some

    case _ => none
  }

  val visible: List[Command] = // exact values in these instances dont matter
    List(Ban(""), Lookup(""), UpdateAdmins)

  case class Ban(reason: String) extends Command("/ban", "забанити акаунт")
  case object BanWithoutReason   extends Command("", "") // hidden command

  case class Lookup(username: String) extends Command("/lookup", "перевірити причину бану")

  case object UpdateAdmins
      extends Command(
        "/update_admins",
        "оновити список діючих адмінів (кеш бота)",
      )

  // TODO: implement this later
  // case class NotifyAdmins(message: String)
  //     extends Command("/notifyAdmins", "повідомити адмінів")
}
