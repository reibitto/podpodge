import com.typesafe.sbt.packager.docker._
import sbt._
import sbt.Keys._
import sbtwelcome._

Global / excludeLintKeys ++= Set(
  executableScriptName,
  daemonStdoutLogFile,
  rpmScriptsDirectory,
  name,
  sourceDirectory
)

lazy val root = project
  .in(file("."))
  .aggregate(core)
  .settings(
    name := "podpodge-root",
    ThisBuild / version := Build.PodpodgeVersion,
    addCommandAlias("run", "podpodge/run"),
    // Scoped to podpodge/ since a bare `assembly` would also build an empty jar for the root project.
    addCommandAlias("uberjar", "podpodge/assembly"),
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
      UsefulTask("~podpodge/run", "Runs the Podpodge server with file-watch enabled"),
      UsefulTask("~compile", "Compile all modules with file-watch enabled"),
      UsefulTask("uberjar", "Builds a standalone executable jar, runnable with java -jar"),
      UsefulTask("Docker/publishLocal", "Builds a Docker image (bundles yt-dlp, ffmpeg and deno)"),
      UsefulTask("fmt", "Run scalafmt on the entire project")
    )
  )

lazy val core = module("podpodge", Some("core"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    fork := true,
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    // Quill's generated code relies on structural types, which -Xsource:3 flags as fatal by default.
    scalacOptions += "-Wconf:cat=scala3-migration:s",
    Compile / mainClass := Some("podpodge.Main"),
    // Every zio-test spec has a `main`, so sbt warns about multiple candidates for `Test / run`.
    Test / mainClass := None,
    assembly / mainClass := Some("podpodge.Main"),
    assembly / assemblyJarName := s"podpodge-${version.value}.jar",
    assembly / assemblyMergeStrategy := {
      // Each pekko module ships its own reference.conf and all of them are needed.
      case "reference.conf" => MergeStrategy.concat
      // Only read by derivation macros at compile time.
      case "deriving.conf" => MergeStrategy.discard
      // Conflicting module-info.class files from multi-release jars. Not needed since this isn't a JPMS module.
      case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
      // Swagger UI reads its version from this metadata at startup. See
      // https://tapir.softwaremill.com/en/latest/docs/openapi.html#using-swaggerui-with-sbt-assembly
      case PathList("META-INF", "maven", "org.webjars", "swagger-ui", _*) => MergeStrategy.first
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    // Docker (sbt-native-packager)
    Docker / packageName := "podpodge",
    // Fixed path (stage dir is target/docker/stage) so the CI workflow doesn't depend on the Scala version.
    Docker / target := (ThisBuild / baseDirectory).value / "target" / "docker",
    dockerBaseImage := "eclipse-temurin:21-jre-jammy",
    dockerExposedPorts := Seq(8080),
    dockerExposedVolumes := Seq("/opt/docker/data"),
    dockerUpdateLatest := true,
    // Containers must listen on 0.0.0.0. PODPODGE_HOST (the address in RSS feeds) is left for the user to set.
    dockerEnvVars := Map("PODPODGE_BIND_HOST" -> "0.0.0.0"),
    // The default multi-stage strategy adds FROM stages that would break the RUN insertion below.
    dockerPermissionStrategy := DockerPermissionStrategy.CopyChown,
    dockerLabels := Map("org.opencontainers.image.source" -> "https://github.com/reibitto/podpodge"),
    // Installs yt-dlp, ffmpeg (audio extraction and ffprobe) and deno (JS runtime yt-dlp needs for YouTube's
    // signature challenges). Inserted right after FROM so it runs as root.
    dockerCommands := dockerCommands.value.flatMap {
      case cmd @ Cmd("FROM", _*) =>
        // One RUN so the apt cleanup shrinks the layer. Shell form is needed for `&&`, globs and pipes.
        List(
          cmd,
          Cmd(
            "RUN",
            "apt-get update" +
              " && apt-get install -y --no-install-recommends ffmpeg python3 curl unzip ca-certificates" +
              // -f fails the build on HTTP errors instead of saving the error page as the binary.
              " && curl -fL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp" +
              " && chmod a+rx /usr/local/bin/yt-dlp" +
              " && curl -fsSL https://deno.land/install.sh | sh" +
              " && mv /root/.deno/bin/deno /usr/local/bin/deno" +
              " && chmod a+rx /usr/local/bin/deno" +
              " && apt-get purge -y unzip" +
              " && apt-get clean" +
              " && rm -rf /var/lib/apt/lists/*"
          )
        )
      case other => List(other)
    },
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
      "org.slf4j" % "slf4j-nop" % V.slf4j,
      "dev.zio" %% "zio-test" % V.zio % Test,
      "dev.zio" %% "zio-test-sbt" % V.zio % Test
    )
  )

def module(projectId: String, moduleFile: Option[String] = None): Project =
  Project(id = projectId, base = file(moduleFile.getOrElse(projectId)))
    .settings(Build.defaultSettings(projectId))
