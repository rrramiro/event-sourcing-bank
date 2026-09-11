name := "event-sourcing-bank"

version := "0.0.1"

scalaVersion := "2.13.18"

val http4sVersion     = "0.23.37"
val circeVersion      = "0.14.4"
val fs2Version        = "3.14.0"
val monixVersion      = "3.2.2"
val sttpVersion       = "3.11.0"
val refinedVersion    = "0.11.4"
val silencerVersion   = "1.7.19"
val catsEffectVersion = "3.7.1"
val catsVersion       = "2.13.0"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-mtl"      % "1.7.0",
  "org.typelevel" %% "cats-core"     % catsVersion,
  "org.typelevel" %% "cats-kernel"   % catsVersion,
  "org.typelevel" %% "cats-effect"   % catsEffectVersion,
  "eu.timepit"    %% "refined"       % refinedVersion,
  "eu.timepit"    %% "refined-cats"  % refinedVersion,
  "org.http4s"    %% "http4s-core"   % http4sVersion,
  "org.http4s"    %% "http4s-server" % http4sVersion,
  "org.http4s"    %% "http4s-dsl"    % http4sVersion,
  //"org.http4s"                   %% "http4s-blaze-server"  % http4sVersion,
  "org.http4s"                    %% "http4s-ember-server"  % http4sVersion,
  "org.http4s"                    %% "http4s-circe"         % http4sVersion,
  "io.circe"                      %% "circe-core"           % circeVersion,
  "io.circe"                      %% "circe-generic"        % circeVersion,
  "io.circe"                      %% "circe-literal"        % circeVersion,
  "io.circe"                      %% "circe-refined"        % circeVersion,
  "io.circe"                      %% "circe-generic-extras" % circeVersion,
  "co.fs2"                        %% "fs2-reactive-streams" % fs2Version,
  "co.fs2"                        %% "fs2-core"             % fs2Version,
  "org.scalatest"                 %% "scalatest"            % "3.2.20"        % Test,
  "com.softwaremill.sttp.client3" %% "core"                 % sttpVersion     % Test,
  "com.softwaremill.sttp.client3" %% "http4s-backend"       % sttpVersion     % Test,
  "com.softwaremill.sttp.client3" %% "circe"                % sttpVersion,
  "com.chuusai"                   %% "shapeless"            % "2.3.13",
  "com.github.ghik"                % "silencer-lib"         % silencerVersion % "provided" cross CrossVersion.full,
  "ch.qos.logback"                 % "logback-classic"      % "1.2.3",
  compilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.3.1"),
  compilerPlugin("org.typelevel"  %% "kind-projector"     % "0.13.4" cross CrossVersion.full),
  compilerPlugin("io.tryp"         % "splain"             % "1.2.0" cross CrossVersion.patch),
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
  Wart.OptionPartial,
  Wart.ObjectThrowable
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
