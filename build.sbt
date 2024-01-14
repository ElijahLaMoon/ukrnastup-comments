import Dependencies._
import org.typelevel.scalacoptions.ScalacOptions

ThisBuild / version := "0.1.0"
ThisBuild / organization := "org.ukrnastup"
ThisBuild / organizationName := "UkrNastup"

ThisBuild / libraryDependencies += compilerPlugin(
  "com.olegpy" %% "better-monadic-for" % "0.3.1"
)

Global / onLoad := {
  (Global / onLoad).value andThen ("dependencyUpdates" :: _)
}

lazy val baseSettings = Seq(
  scalaVersion := "2.13.12",
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  tpolecatScalacOptions ++= Set(
    ScalacOptions.deprecation,
    ScalacOptions.lintAdaptedArgs,
    ScalacOptions.lintConstant,
    ScalacOptions.lintInferAny,
    ScalacOptions.lintMissingInterpolator,
    ScalacOptions.lintPrivateShadow,
    ScalacOptions.lintTypeParameterShadow,
    ScalacOptions.lintDeprecation,
    ScalacOptions.lintImplicitNotFound,
    ScalacOptions.lintInaccessible,
    ScalacOptions.warnDeadCode,
    ScalacOptions.warnValueDiscard
  ) ++ ScalacOptions.warnUnusedOptions,
  tpolecatExcludeOptions ++= ScalacOptions.fatalWarningOptions, // i dont know why, but by default warnings are fatal
  console / tpolecatExcludeOptions ++= ScalacOptions.defaultConsoleExclude
)

lazy val dockerSettings = Seq(
  (docker / dockerfile) := NativeDockerfile(file("Dockerfile")),
  (docker / imageNames) := Seq(
    // Sets the latest tag
    ImageName(s"${organization.value}/${name.value}:latest"),

    // Sets a name with a tag that contains the project version
    ImageName(
      namespace = Some(organization.value),
      repository = name.value,
      tag = Some(version.value)
    )
  )
)

lazy val root = project
  .in(file("."))
  .settings(baseSettings)
  .settings(
    name := "comments-root",
    publishArtifact := false
  )
  .aggregate(comments)

lazy val comments = project
  .in(file("comments"))
  .settings(baseSettings, dockerSettings)
  .settings(
    name := "comments"
  )
  .enablePlugins(JavaAppPackaging, sbtdocker.DockerPlugin)
  .settings(
    libraryDependencies ++= Seq(
      cats,
      catsEffect,
      fs2Core,
      http4sEmberClient,
      http4sEmberServer,
      http4sDsl,
      telegramiumHigh,
      logstage,
      logstageSlf4j,
      quillJdbc,
      quillDoobie,
      sqliteJdbc,
      doobieCore,
      doobieHikari,
      fly4s
      // jsoniter, // these arent used for now
      // distageCore
    )
  )

addCommandAlias("run", "comments/run")
