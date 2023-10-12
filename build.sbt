import sbt._
import sbt.Keys._
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
      UsefulTask("run", "Runs the Podpodge server"),
      UsefulTask("~podpodge/reStart", "Runs the Podpodge server with file-watch enabled"),
      UsefulTask("~compile", "Compile all modules with file-watch enabled"),
      UsefulTask("fmt", "Run scalafmt on the entire project")
    )
  )

lazy val core = module("podpodge", Some("core"))
  .settings(
    fork := true,
    run / baseDirectory := file("."),
    reStart / baseDirectory := file("."),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % V.zio,
      "dev.zio" %% "zio-streams" % V.zio,
      "dev.zio" %% "zio-logging" % V.zioLogging,
      "dev.zio" %% "zio-process" % V.zioProcess,
      "dev.zio" %% "zio-prelude" % V.zioPrelude,
      "org.scala-lang.modules" %% "scala-xml" % V.scalaXml,
      "com.beachape" %% "enumeratum" % V.enumeratum,
      "com.beachape" %% "enumeratum-circe" % V.enumeratum,
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-parser" % V.circe,
      "io.circe" %% "circe-generic" % V.circe,
      "org.apache.pekko" %% "pekko-http" % V.pekkoHttp,
      "org.apache.pekko" %% "pekko-actor-typed" % V.pekko,
      "org.apache.pekko" %% "pekko-stream" % V.pekko,
      "com.softwaremill.sttp.client3" %% "core" % V.sttp,
      "com.softwaremill.sttp.client3" %% "circe" % V.sttp,
      "com.softwaremill.sttp.client3" %% "zio" % V.sttp,
      "com.softwaremill.sttp.tapir" %% "tapir-enumeratum" % V.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % V.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % V.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % V.tapir,
      "io.getquill" %% "quill-jdbc-zio" % V.quill,
      "org.xerial" % "sqlite-jdbc" % V.sqliteJdbc,
      "org.flywaydb" % "flyway-core" % V.flyway,
      "org.slf4j" % "slf4j-nop" % V.slf4j
    )
  )

def module(projectId: String, moduleFile: Option[String] = None): Project =
  Project(id = projectId, base = file(moduleFile.getOrElse(projectId)))
    .settings(Build.defaultSettings(projectId))
