name := "event-sourcing-bank"

version := "0.0.1"

scalaVersion := "2.13.6"

val http4sVersion   = "0.23.10"
val circeVersion    = "0.14.1"
val fs2Version      = "3.2.4"
val monixVersion    = "3.2.2"
val sttpVersion     = "3.5.0"
val refinedVersion  = "0.9.28"
val silencerVersion = "1.7.8"
val catsVersion     = "2.7.0"

libraryDependencies ++= Seq(
  "org.typelevel"                %% "cats-mtl"             % "1.2.1",
  "org.typelevel"                %% "cats-core"            % catsVersion,
  "org.typelevel"                %% "cats-effect"          % "3.3.5",
  "org.typelevel"                %% "cats-kernel"          % catsVersion,
  "eu.timepit"                   %% "refined"              % refinedVersion,
  "eu.timepit"                   %% "refined-cats"         % refinedVersion,
  "org.http4s"                   %% "http4s-core"          % http4sVersion,
  "org.http4s"                   %% "http4s-server"        % http4sVersion,
  "org.http4s"                   %% "http4s-dsl"           % http4sVersion,
  "org.http4s"                   %% "http4s-blaze-server"  % http4sVersion,
  "org.http4s"                   %% "http4s-circe"         % http4sVersion,
  "io.circe"                     %% "circe-core"           % circeVersion,
  "io.circe"                     %% "circe-generic"        % circeVersion,
  "io.circe"                     %% "circe-literal"        % circeVersion,
  "io.circe"                     %% "circe-refined"        % circeVersion,
  "io.circe"                     %% "circe-generic-extras" % circeVersion,
  "co.fs2"                       %% "fs2-reactive-streams" % fs2Version,
  "co.fs2"                       %% "fs2-core"             % fs2Version,
  "org.scalatest"                %% "scalatest"            % "3.2.11"    % Test,
  "com.softwaremill.sttp.client3" %% "core"                 % sttpVersion % Test,
  "com.softwaremill.sttp.client3" %% "http4s-backend"       % sttpVersion % Test,
  "com.softwaremill.sttp.client3" %% "circe"                % sttpVersion,
  "com.chuusai"                  %% "shapeless"            % "2.3.8",
  "com.github.ghik"               % "silencer-lib"         % silencerVersion     % "provided" cross CrossVersion.full,
  "ch.qos.logback"                % "logback-classic"      % "1.2.10",
  compilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.3.1"),
  compilerPlugin("org.typelevel"  %% "kind-projector"     % "0.13.2" cross CrossVersion.full),
  compilerPlugin("io.tryp"         % "splain"             % "1.0.0" cross CrossVersion.patch),
  compilerPlugin("com.github.ghik" % "silencer-plugin"    % silencerVersion cross CrossVersion.full)
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-explaintypes",
  "-unchecked",
  "-encoding",
  "UTF-8",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
  "-Xfatal-warnings",
  "-Xlint:-infer-any,-byname-implicit,_",
  "-Xlog-reflective-calls",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
  "-Ywarn-numeric-widen",
  "-Ywarn-extra-implicit",
  "-Ywarn-unused:_"
)

Compile / compile / wartremoverErrors ++= Warts.allBut(
  Wart.Any,
  Wart.Nothing,
  Wart.Serializable,
  Wart.PlatformDefault,
  Wart.ListAppend,
  Wart.GlobalExecutionContext
)

Global / onChangedBuildSource := ReloadOnSourceChanges

missinglinkExcludedDependencies ++= Seq(
  moduleFilter(organization = "ch.qos.logback", name = "logback-core"),
  moduleFilter(organization = "ch.qos.logback", name = "logback-classic")
)
/*
dependencyOverrides ++= Seq(
  "co.fs2"                       %% "fs2-core" % fs2Version
)
conflictManager := ConflictManager.strict
*/
