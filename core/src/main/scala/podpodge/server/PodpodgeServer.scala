package podpodge.server

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import podpodge.*
import podpodge.config.PodpodgeConfig
import podpodge.controllers.PodcastController
import podpodge.types.{EpisodeId, ServerHost}
import podpodge.util.Browser
import zio.*

import java.io.File

object PodpodgeServer {

  def make: RIO[Scope & Env, Http.ServerBinding] = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "podpodge-system")

    for {
      config              <- config.get
      downloadQueue       <- Queue.unbounded[CreateEpisodeRequest]
      _                   <- DownloadWorker.make(downloadQueue).forkDaemon
      episodesDownloading <- Ref.Synchronized.make(Map.empty[EpisodeId, Promise[Throwable, File]])
      case implicit0(runtime: Runtime[Env]) <- ZIO.runtime[Env]
      (server, config) <-
        ZIO.acquireRelease(
          startServer(config, downloadQueue, episodesDownloading)
            .map(server => (server, config))
            .catchSome {
              // Fallback if server host is misconfigured. That way the configuration route can still be accessed
              // and fixed through the API.
              case t: Throwable if config.serverHost != ServerHost.localhost =>
                val localhostConfig = config.copy(serverHost = ServerHost.localhost)

                for {
                  _ <- ZIO
                         .logWarningCause(
                           s"Unable to start server at ${config.baseUri}. Trying localhost instead.",
                           Cause.fail(t)
                         )
                  server <- startServer(localhostConfig, downloadQueue, episodesDownloading)
                } yield (server, localhostConfig)

            }
        ) { case (server, _) => shutdownServer(server, system).orDie }
      _ <- Browser.open(config.baseUri).when(config.openBrowser.unwrap)
      _ <- PodcastController.checkForUpdatesAll(downloadQueue).when(config.autoCheckAllPodcastUpdates.unwrap)
    } yield server
  }

  private def startServer(
      config: PodpodgeConfig,
      downloadQueue: Queue[CreateEpisodeRequest],
      episodesDownloading: Ref.Synchronized[Map[EpisodeId, Promise[Throwable, File]]]
  )(implicit runtime: Runtime[Env], system: ActorSystem[Nothing]): Task[Http.ServerBinding] =
    ZIO.fromFuture { _ =>
      Http()
        .newServerAt(config.serverHost.unwrap, config.serverPort.unwrap)
        .bind(Routes.make(downloadQueue, episodesDownloading)(runtime))
    }.tap { _ =>
      ZIO.logInfo(s"Started server at ${config.renderBaseUri}")
    }

  private def shutdownServer(server: Http.ServerBinding, system: ActorSystem[Nothing]): Task[Unit] =
    for {
      _ <- ZIO.logInfo("Shutting down server")
      _ <- ZIO.fromFuture(_ => server.unbind())
      _ <- ZIO.attempt(system.terminate())
      _ <- ZIO.fromFuture(_ => system.whenTerminated)
    } yield ()
}
