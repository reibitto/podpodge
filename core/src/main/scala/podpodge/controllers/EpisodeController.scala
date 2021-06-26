package podpodge.controllers

import java.io.File
import java.nio.file.Paths
import akka.http.scaladsl.model.{ HttpEntity, MediaType, MediaTypes, StatusCodes }
import akka.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import akka.stream.scaladsl.{ FileIO, StreamConverters }
import podpodge.StaticConfig
import podpodge.db.Episode
import podpodge.db.dao.{ EpisodeDao, PodcastDao }
import podpodge.http.HttpError
import podpodge.types._
import podpodge.youtube.YouTubeDL
import zio.blocking.Blocking
import zio.logging.{ log, Logging }
import zio._

import java.sql.Connection

object EpisodeController {

  def getEpisodeFile(id: EpisodeId): RIO[Has[Connection] with Blocking, HttpEntity.Default] =
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
      promiseMap <- episodesDownloading.updateAndGet { downloadMap =>
                      downloadMap.get(episode.id) match {
                        case None    =>
                          for {
                            p <- Promise.make[Throwable, File]
                            _ <- YouTubeDL
                                   .download(episode.podcastId, episode.externalSource)
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

  def getThumbnail(id: EpisodeId): RIO[Has[Connection] with Blocking, HttpEntity.Default] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      result  <- episode.imagePath.map(_.toFile) match {
                   case Some(imageFile) if imageFile.exists() =>
                     UIO(HttpEntity.Default(MediaTypes.`image/png`, imageFile.length, FileIO.fromPath(imageFile.toPath)))

                   case _ =>
                     Option(getClass.getResource("/question.png")).flatMap(ResourceFile.apply) match {
                       case None           => ZIO.fail(HttpError(StatusCodes.InternalServerError))
                       case Some(resource) =>
                         UIO(
                           HttpEntity.Default(
                             MediaTypes.`image/jpeg`,
                             resource.length,
                             StreamConverters.fromInputStream(() => resource.url.openStream())
                           )
                         )
                     }
                 }
    } yield result

}
