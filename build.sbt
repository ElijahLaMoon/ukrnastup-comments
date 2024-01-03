import Dependencies._

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "org.ukrnastup"
ThisBuild / organizationName := "UkrNastup"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-Xlint:unused",
  "-Xlint:adapted-args",
  "-Xlint:constant",
  "-Xlint:infer-any",
  "-Xlint:missing-interpolator",
  "-Xlint:private-shadow",
  "-Xlint:type-parameter-shadow",
  "-Xlint:deprecation",
  "-Xlint:implicit-not-found",
  "-Xlint:inaccessible",
  "-Wdead-code",
  "-Wvalue-discard"
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "Ukrnastup Comments",
    libraryDependencies += munit % Test
  )
  .aggregate(comments)

lazy val comments = project
  .in(file("comments"))
  .settings(
    name := "comments"
  )
  .settings(
    libraryDependencies ++= Seq(
      cats,
      catsEffect,
      fs2Core,
      http4sEmberClient,
      http4sEmberServer,
      http4sDsl,
      jsoniter,
      // telegramiumCore,
      telegramiumHigh,
      distageCore,
      quillJdbc,
      quillDoobie,
      sqliteJdbc,
      doobieCore,
      doobieHikari,
      fly4s
    )
  )
