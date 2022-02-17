name := "event-sourcing-bank"

version := "0.0.1"

scalaVersion := "2.13.3"

val http4sVersion   = "0.21.7"
val circeVersion    = "0.13.0"
val fs2Version      = "2.4.4"
val monixVersion    = "3.2.2"
val sttpVersion     = "2.2.9"
val refinedVersion  = "0.9.16"
val silencerVersion = "1.7.1"
val catsVersion     = "2.2.0"

libraryDependencies ++= Seq(
  "org.typelevel"                %% "cats-mtl"             % "1.0.0",
  "org.typelevel"                %% "cats-core"            % catsVersion,
  "org.typelevel"                %% "cats-effect"          % catsVersion,
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
  "org.scalatest"                %% "scalatest"            % "3.2.2"     % Test,
  "com.softwaremill.sttp.client" %% "core"                 % sttpVersion % Test,
  "com.softwaremill.sttp.client" %% "http4s-backend"       % sttpVersion % Test,
  "com.softwaremill.sttp.client" %% "circe"                % sttpVersion,
  "com.chuusai"                  %% "shapeless"            % "2.3.3",
  "com.github.ghik"               % "silencer-lib"         % silencerVersion     % "provided" cross CrossVersion.full,
  "ch.qos.logback"                % "logback-classic"      % "1.2.3",
  compilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.3.1"),
  compilerPlugin("org.typelevel"  %% "kind-projector"     % "0.11.0" cross CrossVersion.full),
  compilerPlugin("io.tryp"         % "splain"             % "0.5.7" cross CrossVersion.patch),
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

wartremoverErrors in (Compile, compile) ++= Warts.allBut(
  Wart.Any,
  Wart.Nothing,
  Wart.Serializable
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
