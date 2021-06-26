package podpodge.server

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import podpodge.config.Config
import podpodge.types.EpisodeId
import podpodge.{ config, CreateEpisodeRequest, DownloadWorker, StaticConfig }
import sttp.client.httpclient.zio.SttpClient
import zio._
import zio.blocking.Blocking
import zio.logging.{ log, Logging }

import java.io.File
import java.sql.Connection

object PodpodgeServer {
  def make: ZManaged[Logging with Has[
    Connection
  ] with Blocking with SttpClient with Config, Throwable, Http.ServerBinding] = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "podpodge-system")

    for {
      _                   <- StaticConfig.ensureDirectoriesExist.toManaged_
      config              <- config.get.toManaged_
      downloadQueue       <- ZManaged.fromEffect(ZQueue.unbounded[CreateEpisodeRequest])
      _                   <- DownloadWorker.make(downloadQueue).forkDaemon.toManaged_
      episodesDownloading <- ZRefM.makeManaged(Map.empty[EpisodeId, Promise[Throwable, File]])
      server              <- ZManaged.make(
                               Task.fromFuture { _ =>
                                 Http()
                                   .newServerAt(config.serverHost.unwrap, config.serverPort.unwrap)
                                   .bind(Routes.make(downloadQueue, episodesDownloading))
                               } <* log.info(s"Starting server at ${config.baseUri}")
                             )(server =>
                               (for {
                                 _ <- log.info("Shutting down server")
                                 _ <- Task.fromFuture(_ => server.unbind())
                                 _ <- Task(system.terminate())
                                 _ <- Task.fromFuture(_ => system.whenTerminated)
                               } yield ()).orDie
                             )
    } yield server
  }
}
