import sbt._

object Dependencies {

  object V { // versions
    val cats = "2.10.0"
    val catsEffect = "3.5.3"
    val fs2 = "3.9.3"
    val http4s = "0.23.25"
    val jsoniter = "2.27.2"
    val telegramium = "8.70.0"
    val izumi = "1.2.4"
    val quill = "4.8.1"
    val sqliteJdbc = "3.45.0.0"
    val doobie = "1.0.0-RC4" // TODO: update on stable release
    val fly4s = "1.0.0"
  }

  object O { // organizations
    val typelevel = "org.typelevel"
    val fs2 = "co.fs2"
    val http4s = "org.http4s"
    val jsoniter = "com.github.plokhotnyuk.jsoniter-scala"
    val telegramium = "io.github.apimorphism"
    val izumi = "io.7mind.izumi"
    val quill = "io.getquill"
    val xerial = "org.xerial"
    val tpolecat = "org.tpolecat"
    val geirolz = "com.github.geirolz"
  }

  // --------------------------------------------------
  lazy val cats = O.typelevel %% "cats-core" % V.cats
  lazy val catsEffect = O.typelevel %% "cats-effect" % V.catsEffect
  lazy val fs2Core = O.fs2 %% "fs2-core" % V.fs2
  lazy val http4sEmberClient = O.http4s %% "http4s-ember-client" % V.http4s
  lazy val http4sEmberServer = O.http4s %% "http4s-ember-server" % V.http4s
  lazy val http4sDsl = O.http4s %% "http4s-dsl" % V.http4s
  lazy val jsoniter = O.jsoniter %% "jsoniter-scala-core" % V.jsoniter
  lazy val telegramiumCore = O.telegramium %% "telegramium-core" % V.telegramium
  lazy val telegramiumHigh = O.telegramium %% "telegramium-high" % V.telegramium
  lazy val distageCore = O.izumi %% "distage-core" % V.izumi
  lazy val logstage = O.izumi %% "logstage-core" % V.izumi
  lazy val logstageSlf4j = O.izumi %% "logstage-adapter-slf4j" % V.izumi
  lazy val quillJdbc = O.quill %% "quill-jdbc" % V.quill
  lazy val quillDoobie = O.quill %% "quill-doobie" % V.quill
  lazy val sqliteJdbc = O.xerial % "sqlite-jdbc" % V.sqliteJdbc
  lazy val doobieCore = O.tpolecat %% "doobie-core" % V.doobie
  lazy val doobieHikari = O.tpolecat %% "doobie-hikari" % V.doobie
  lazy val fly4s = O.geirolz %% "fly4s-core" % V.fly4s

  lazy val munit = "org.scalameta" %% "munit" % "0.7.29"
}
