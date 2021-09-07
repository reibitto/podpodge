import Build.Version
import sbt.Keys._
import sbt._
import sbtwelcome._

lazy val root = project
  .in(file("."))
  .aggregate(core)
  .settings(
    name        := "podpodge",
    addCommandAlias("run", "podpodge/run"),
    addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll"),
    addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll"),
    logo        :=
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
    fork                    := true,
    run / baseDirectory     := file("."),
    reStart / baseDirectory := file("."),
    libraryDependencies ++= Seq(
      "dev.zio"                      %% "zio"                        % Version.zio,
      "dev.zio"                      %% "zio-streams"                % Version.zio,
      "dev.zio"                      %% "zio-process"                % "0.5.0",
      "dev.zio"                      %% "zio-config"                 % "1.0.6",
      "dev.zio"                      %% "zio-logging"                % "0.5.12",
      "dev.zio"                      %% "zio-prelude"                % "1.0.0-RC6",
      "io.github.kitlangton"         %% "zio-magic"                  % "0.3.8",
      "org.scala-lang.modules"       %% "scala-xml"                  % "2.0.1",
      "com.beachape"                 %% "enumeratum"                 % Version.enumeratum,
      "com.beachape"                 %% "enumeratum-circe"           % Version.enumeratum,
      "io.circe"                     %% "circe-core"                 % Version.circe,
      "io.circe"                     %% "circe-parser"               % Version.circe,
      "io.circe"                     %% "circe-generic"              % Version.circe,
      "com.typesafe.akka"            %% "akka-http"                  % "10.2.6",
      "com.typesafe.akka"            %% "akka-actor-typed"           % "2.6.16",
      "com.typesafe.akka"            %% "akka-stream"                % "2.6.16",
      "com.softwaremill.sttp.client" %% "core"                       % Version.sttp,
      "com.softwaremill.sttp.client" %% "circe"                      % Version.sttp,
      "com.softwaremill.sttp.client" %% "httpclient-backend-zio"     % Version.sttp,
      "com.softwaremill.sttp.tapir"  %% "tapir-enumeratum"           % Version.tapir,
      "com.softwaremill.sttp.tapir"  %% "tapir-json-circe"           % Version.tapir,
      "com.softwaremill.sttp.tapir"  %% "tapir-akka-http-server"     % Version.tapir,
      "com.softwaremill.sttp.tapir"  %% "tapir-openapi-docs"         % Version.tapir,
      "com.softwaremill.sttp.tapir"  %% "tapir-openapi-circe-yaml"   % Version.tapir,
      "com.softwaremill.sttp.tapir"  %% "tapir-swagger-ui-akka-http" % Version.tapir,
      "io.getquill"                  %% "quill-jdbc"                 % Version.quill,
      "io.getquill"                  %% "quill-jdbc-zio"             % Version.quill,
      "org.xerial"                    % "sqlite-jdbc"                % "3.36.0.3",
      "org.flywaydb"                  % "flyway-core"                % "7.15.0",
      "org.slf4j"                     % "slf4j-nop"                  % "1.7.32"
    )
  )

def module(projectId: String, moduleFile: Option[String] = None): Project =
  Project(id = projectId, base = file(moduleFile.getOrElse(projectId)))
    .settings(Build.defaultSettings(projectId))
