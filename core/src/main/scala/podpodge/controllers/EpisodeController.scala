package podpodge.controllers

import akka.http.scaladsl.model.{ HttpEntity, MediaType, StatusCodes }
import akka.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import akka.stream.IOResult
import akka.stream.scaladsl.{ FileIO, Source, StreamConverters }
import akka.util.ByteString
import podpodge.StaticConfig
import podpodge.db.Episode
import podpodge.db.dao.{ ConfigurationDao, EpisodeDao, PodcastDao }
import podpodge.http.HttpError
import podpodge.types._
import podpodge.youtube.YouTubeDL
import zio._
import zio.blocking.Blocking
import zio.logging.{ log, Logging }

import java.io.File
import java.nio.file.Paths
import java.sql.Connection
import scala.concurrent.Future

object EpisodeController {

  def getEpisodeFile(id: EpisodeId): RIO[Has[Connection], HttpEntity.Default] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      file    <-
        UIO(
          StaticConfig.audioPath
            .resolve(episode.podcastId.unwrap.toString)
            .resolve(s"${episode.externalSource}.mp3")
            .toFile
        ).filterOrFail(_.exists)(HttpError(StatusCodes.NotFound))
    } yield HttpEntity.Default(
      MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
      file.length,
      FileIO.fromPath(file.toPath)
    )

  def getEpisodeFileOnDemand(
    episodesDownloading: RefM[Map[EpisodeId, Promise[Throwable, File]]]
  )(id: EpisodeId): RIO[Has[Connection] with Blocking with Logging, HttpEntity.Default] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      podcast <- PodcastDao.get(episode.podcastId).someOrFail(HttpError(StatusCodes.NotFound))
      _       <- log.info(s"Requested episode '${episode.title}' on demand")
      result  <- podcast.sourceType match {
                   case SourceType.YouTube =>
                     getEpisodeFileOnDemandYouTube(episodesDownloading)(episode)

                   case SourceType.Directory =>
                     val mediaPath = Paths.get(episode.externalSource)

                     UIO(
                       HttpEntity.Default(
                         MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
                         mediaPath.toFile.length,
                         FileIO.fromPath(mediaPath)
                       )
                     )
                 }
    } yield result

  def getEpisodeFileOnDemandYouTube(
    episodesDownloading: RefM[Map[EpisodeId, Promise[Throwable, File]]]
  )(episode: Episode.Model): RIO[Has[Connection] with Blocking with Logging, HttpEntity.Default] =
    for {
      config     <- ConfigurationDao.getPrimary
      promiseMap <- episodesDownloading.updateAndGet { downloadMap =>
                      downloadMap.get(episode.id) match {
                        case None =>
                          for {
                            p <- Promise.make[Throwable, File]
                            _ <- YouTubeDL
                                   .download(episode.podcastId, episode.externalSource, config.downloaderPath)
                                   .onExit { e =>
                                     e.toEither.fold(p.fail, p.succeed) *>
                                       episodesDownloading.updateAndGet(m => UIO(m - episode.id))
                                   }
                                   .forkDaemon
                          } yield downloadMap + (episode.id -> p)

                        case Some(_) => UIO(downloadMap)
                      }
                    }
      mediaFile  <- promiseMap(episode.id).await
      _          <- EpisodeDao.updateMediaFile(episode.id, Some(mediaFile.getName))
    } yield HttpEntity.Default(
      MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
      mediaFile.length,
      FileIO.fromPath(mediaFile.toPath)
    )

  def getThumbnail(id: EpisodeId): RIO[Has[Connection], Source[ByteString, Future[IOResult]]] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      result  <- episode.imagePath.map(_.toFile) match {
                   case Some(imageFile) if imageFile.exists() =>
                     UIO(FileIO.fromPath(imageFile.toPath))

                   case _ =>
                     Option(getClass.getResource("/question.png")).flatMap(ResourceFile.apply) match {
                       case None           => ZIO.fail(HttpError(StatusCodes.InternalServerError))
                       case Some(resource) =>
                         UIO(StreamConverters.fromInputStream(() => resource.url.openStream()))
                     }
                 }
    } yield result

}
