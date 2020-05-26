name := "event-sourcing-bank"

version := "0.0.1"

scalaVersion := "2.13.1"

val http4sVersion   = "0.21.4"
val circeVersion    = "0.13.0"
val fs2Version      = "2.3.0"
val sttpVersion     = "2.1.5"
val refinedVersion  = "0.9.14"
val silencerVersion = "1.6.0"

libraryDependencies ++= Seq(
  "org.typelevel"                %% "cats-mtl-core"        % "0.7.1",
  "eu.timepit"                   %% "refined"              % refinedVersion,
  "eu.timepit"                   %% "refined-cats"         % refinedVersion,
  "org.http4s"                   %% "http4s-dsl"           % http4sVersion,
  "org.http4s"                   %% "http4s-blaze-server"  % http4sVersion,
  "org.http4s"                   %% "http4s-circe"         % http4sVersion,
  "io.circe"                     %% "circe-generic"        % circeVersion,
  "io.circe"                     %% "circe-literal"        % circeVersion,
  "io.circe"                     %% "circe-refined"        % circeVersion,
  "io.circe"                     %% "circe-generic-extras" % circeVersion,
  "co.fs2"                       %% "fs2-reactive-streams" % fs2Version,
  "org.scalatest"                %% "scalatest"            % "3.1.2"     % Test,
  "com.softwaremill.sttp.client" %% "core"                 % sttpVersion % Test,
  "com.softwaremill.sttp.client" %% "http4s-backend"       % sttpVersion % Test,
  "com.softwaremill.sttp.client" %% "circe"                % sttpVersion,
  "com.github.ghik"               % "silencer-lib"         % "1.6.0"     % "provided" cross CrossVersion.full,
  "ch.qos.logback"                % "logback-classic"      % "1.2.3",
  compilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.3.1"),
  compilerPlugin("org.typelevel"  %% "kind-projector"     % "0.11.0" cross CrossVersion.full),
  compilerPlugin("io.tryp"         % "splain"             % "0.5.6" cross CrossVersion.patch),
  compilerPlugin("com.github.ghik" % "silencer-plugin"    % silencerVersion cross CrossVersion.full)
)

scalacOptions ++= Seq(
  "-encoding",
  "utf8",
  "-Xfatal-warnings",
  "-Xlint:-infer-any,_",
  "-Xlint:constant",
  "-Xlog-reflective-calls",
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
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
