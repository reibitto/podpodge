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
    // Scoped to podpodge/ rather than left to aggregate: a bare `assembly` also builds an assembly jar for this
    // aggregate root project, which contains no code and is never useful.
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
    // zio-test gives every spec a `main` so it can be run standalone, so sbt finds a dozen candidates and warns it
    // can't pick one for `Test / run`. Nothing here uses `Test / run` (specs go through `test` / `testOnly`), so
    // opt out rather than let the warning ride. `Test / runMain <spec>` still works if you want a single spec.
    Test / mainClass := None,
    assembly / mainClass := Some("podpodge.Main"),
    assembly / assemblyJarName := s"podpodge-${version.value}.jar",
    assembly / assemblyMergeStrategy := {
      // Every pekko module ships its own reference.conf; they all need to be present, not just the first one found.
      case "reference.conf" => MergeStrategy.concat
      // Magnolia's derivation config (from circe-generic and zio-json) differs slightly between library versions,
      // but it's only read by their macros at compile time, not bundled at runtime, so it's safe to drop.
      case "deriving.conf" => MergeStrategy.discard
      // Multi-release jars (Jackson, slf4j, sqlite-jdbc, snakeyaml, ...) each ship their own
      // META-INF/versions/*/module-info.class, which conflict since we're not building a JPMS module anyway.
      case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
      // tapir-swagger-ui-bundle reads its own version out of this webjar's Maven metadata at startup; the default
      // strategy drops it, which breaks Swagger UI in the assembled jar with an ExceptionInInitializerError. See
      // https://tapir.softwaremill.com/en/latest/docs/openapi.html#using-swaggerui-with-sbt-assembly
      case PathList("META-INF", "maven", "org.webjars", "swagger-ui", _*) => MergeStrategy.first
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    // --- sbt-native-packager (Docker) ---
    Docker / packageName := "podpodge",
    // Pinned to a stable, predictable path so CI can hand the staged context to `docker buildx`. The default sits
    // under target/out/jvm/scala-<version>/..., which would bake the Scala version into the workflow and silently
    // break it the next time that's bumped. Overriding `target` rather than `stagingDirectory` directly, since
    // native-packager derives the latter from the former (as target/stage) -- pinning the derived key instead
    // leaves `Docker / target` read by nothing, which sbt then flags as an unused key.
    Docker / target := (ThisBuild / baseDirectory).value / "target" / "docker",
    dockerBaseImage := "eclipse-temurin:21-jre-jammy",
    dockerExposedPorts := Seq(8080),
    dockerExposedVolumes := Seq("/opt/docker/data"),
    dockerUpdateLatest := true,
    // A container must listen on 0.0.0.0 or the published port is unreachable from outside its network namespace.
    // Only the *bind* address is set here -- PODPODGE_HOST (what generated RSS feeds tell podcast apps to fetch
    // from) is deliberately left alone, since 0.0.0.0 is not an address any client can resolve. Deployments that
    // serve feeds to other devices set PODPODGE_HOST to a reachable hostname/IP; see the README.
    dockerEnvVars := Map("PODPODGE_BIND_HOST" -> "0.0.0.0"),
    // The default multi-stage permission strategy generates its own FROM stages that bypass our dockerCommands
    // insertion below (for installing ffmpeg/yt-dlp), so pin the classic single-stage COPY --chown Dockerfile shape.
    dockerPermissionStrategy := DockerPermissionStrategy.CopyChown,
    dockerLabels := Map("org.opencontainers.image.source" -> "https://github.com/reibitto/podpodge"),
    // yt-dlp (audio extraction), ffmpeg (used by yt-dlp, and to probe episode duration), and deno (a JS runtime yt-dlp
    // needs to solve YouTube's signature challenges. Without one, downloads increasingly fail or get throttled)
    // aren't part of the base JRE image, so they're installed here. This has to run as root, before the image drops
    // to its unprivileged runtime user, so it's spliced in right after the base FROM rather than appended to
    // dockerCommands.
    dockerCommands := dockerCommands.value.flatMap {
      case cmd @ Cmd("FROM", _*) =>
        // A single shell-form RUN (rather than one per step) so the apt lists cleanup actually shrinks the image
        // instead of just deleting files in a later, still-persisted layer. Shell form (not ExecCmd's JSON/exec
        // form) is required here anyway since `&&`, the `/var/lib/apt/lists/*` glob, and piping into `sh` need a
        // real shell.
        List(
          cmd,
          Cmd(
            "RUN",
            "apt-get update" +
              " && apt-get install -y --no-install-recommends ffmpeg python3 curl unzip ca-certificates" +
              // -f so an HTTP error page isn't silently written to the target and chmod'd into a bogus "executable"
              // that only fails at runtime; curl exits non-zero and fails the build instead.
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
