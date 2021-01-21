import Build.Version
import sbt.Keys._
import sbt._
import sbtwelcome._

lazy val root = project
  .in(file("."))
  .aggregate(core)
  .settings(
    name := "podpodge",
    addCommandAlias("run", "podpodge/run"),
    addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll"),
    addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll"),
    logo :=
      s"""
         |    ____            __                __
         |   / __ \\____  ____/ /___  ____  ____/ /___ ____
         |  / /_/ / __ \\/ __  / __ \\/ __ \\/ __  / __ `/ _ \\
         | / ____/ /_/ / /_/ / /_/ / /_/ / /_/ / /_/ /  __/
         |/_/    \\____/\\__,_/ .___/\\____/\\__,_/\\__, /\\___/
         |                 /_/                /____/
         |
         |""".stripMargin,
    usefulTasks := Seq(
      UsefulTask("a", "run", "Runs the Podpodge server"),
      UsefulTask("b", "~podpodge/reStart", "Runs the Podpodge server with file-watch enabled"),
      UsefulTask("c", "~compile", "Compile all modules with file-watch enabled"),
      UsefulTask("d", "fmt", "Run scalafmt on the entire project")
    )
  )

lazy val core = module("podpodge", Some("core"))
  .settings(
    fork := true,
    baseDirectory in run := file("."),
    baseDirectory in reStart := file("."),
    libraryDependencies ++= Seq(
      "dev.zio"                      %% "zio"                        % Version.zio,
      "dev.zio"                      %% "zio-streams"                % Version.zio,
      "dev.zio"                      %% "zio-process"                % "0.3.0",
      "dev.zio"                      %% "zio-logging"                % "0.5.4",
      "org.scala-lang.modules"       %% "scala-xml"                  % "1.3.0",
      "com.beachape"                 %% "enumeratum"                 % Version.enumeratum,
      "com.beachape"                 %% "enumeratum-circe"           % Version.enumeratum,
      "io.circe"                     %% "circe-core"                 % Version.circe,
      "io.circe"                     %% "circe-parser"               % Version.circe,
      "io.circe"                     %% "circe-generic"              % Version.circe,
      "com.typesafe.akka"            %% "akka-http"                  % "10.2.2",
      "com.typesafe.akka"            %% "akka-actor-typed"           % "2.6.11",
      "com.typesafe.akka"            %% "akka-stream"                % "2.6.11",
      "com.softwaremill.sttp.client" %% "core"                       % Version.sttp,
      "com.softwaremill.sttp.client" %% "circe"                      % Version.sttp,
      "com.softwaremill.sttp.client" %% "httpclient-backend-zio"     % Version.sttp,
      "com.softwaremill.sttp.tapir"  %% "tapir-enumeratum"           % Version.tapir,
      "com.softwaremill.sttp.tapir"  %% "tapir-json-circe"           % Version.tapir,
      "com.softwaremill.sttp.tapir"  %% "tapir-akka-http-server"     % Version.tapir,
      "com.softwaremill.sttp.tapir"  %% "tapir-openapi-docs"         % Version.tapir,
      "com.softwaremill.sttp.tapir"  %% "tapir-openapi-circe-yaml"   % Version.tapir,
      "com.softwaremill.sttp.tapir"  %% "tapir-swagger-ui-akka-http" % Version.tapir,
      "io.getquill"                  %% "quill-jdbc"                 % "3.6.0",
      "org.xerial"                    % "sqlite-jdbc"                % "3.34.0",
      "org.flywaydb"                  % "flyway-core"                % "7.5.1",
      "org.slf4j"                     % "slf4j-nop"                  % "1.7.30"
    )
  )

def module(projectId: String, moduleFile: Option[String] = None): Project =
  Project(id = projectId, base = file(moduleFile.getOrElse(projectId)))
    .settings(Build.defaultSettings(projectId))
