package podpodge.server

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import podpodge.*
import podpodge.types.EpisodeId
import zio.*

import java.io.File

object PodpodgeServer {

  def make: ZIO[Scope & Env, Throwable, Http.ServerBinding] = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "podpodge-system")

    for {
      config              <- config.get
      downloadQueue       <- Queue.unbounded[CreateEpisodeRequest]
      _                   <- DownloadWorker.make(downloadQueue).forkDaemon
      episodesDownloading <- Ref.Synchronized.make(Map.empty[EpisodeId, Promise[Throwable, File]])
      runtime             <- ZIO.runtime[Env]
      server <- ZIO.acquireRelease(
                  ZIO.fromFuture { _ =>
                    Http()
                      .newServerAt(config.serverHost.unwrap, config.serverPort.unwrap)
                      .bind(Routes.make(downloadQueue, episodesDownloading)(runtime))
                  } <* ZIO.logInfo(s"Starting server at ${config.baseUri}")
                )(server =>
                  (for {
                    _ <- ZIO.logInfo("Shutting down server")
                    _ <- ZIO.fromFuture(_ => server.unbind())
                    _ <- ZIO.attempt(system.terminate())
                    _ <- ZIO.fromFuture(_ => system.whenTerminated)
                  } yield ()).orDie
                )
    } yield server
  }
}
