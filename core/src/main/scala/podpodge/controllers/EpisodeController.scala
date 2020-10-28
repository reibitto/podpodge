package podpodge.controllers

import java.io.File

import akka.http.scaladsl.model.{ HttpEntity, MediaType, MediaTypes, StatusCodes }
import akka.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import akka.stream.scaladsl.{ FileIO, StreamConverters }
import podpodge.Config
import podpodge.db.dao.EpisodeDao
import podpodge.http.HttpError
import podpodge.types._
import podpodge.youtube.YouTubeDL
import zio.blocking.Blocking
import zio.logging.Logging
import zio._

object EpisodeController {

  def getEpisodeFile(id: EpisodeId): Task[HttpEntity.Default] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      file    <-
        UIO(
          Config.audioPath.resolve(episode.podcastId.unwrap.toString).resolve(s"${episode.externalSource}.mp3").toFile
        )
          .filterOrFail(_.exists)(HttpError(StatusCodes.NotFound))
    } yield HttpEntity.Default(
      MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
      file.length,
      FileIO.fromPath(file.toPath)
    )

  def getEpisodeFileOnDemand(
    episodesDownloading: RefM[Map[EpisodeId, Promise[Throwable, File]]]
  )(id: EpisodeId): RIO[Blocking with Logging, HttpEntity.Default] =
    for {
      episode    <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      promiseMap <- episodesDownloading.updateAndGet { downloadMap =>
                      downloadMap.get(id) match {
                        case None    =>
                          for {
                            p <- Promise.make[Throwable, File]
                            _ <- YouTubeDL
                                   .download(episode.podcastId, episode.externalSource)
                                   .onExit { e =>
                                     e.toEither.fold(p.fail, p.succeed) *>
                                       episodesDownloading.updateAndGet(m => UIO(m - id))
                                   }
                                   .forkDaemon
                          } yield downloadMap + (id -> p)

                        case Some(_) => UIO(downloadMap)
                      }
                    }
      mediaFile  <- promiseMap(id).await
      _          <- EpisodeDao.updateMediaFile(id, Some(mediaFile.getName))
    } yield HttpEntity.Default(
      MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
      mediaFile.length,
      FileIO.fromPath(mediaFile.toPath)
    )

  def getThumbnail(id: EpisodeId): Task[HttpEntity.Default] =
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
