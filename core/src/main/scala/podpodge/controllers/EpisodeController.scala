package podpodge.controllers

import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.MediaType
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.apache.pekko.stream.IOResult
import org.apache.pekko.util.ByteString
import podpodge.db.dao.ConfigurationDao
import podpodge.db.dao.EpisodeDao
import podpodge.db.dao.PodcastDao
import podpodge.db.Episode
import podpodge.http.HttpError
import podpodge.types.*
import podpodge.youtube.YouTubeDL
import podpodge.StaticConfig
import zio.*

import java.io.File
import java.nio.file.Paths
import javax.sql.DataSource
import scala.concurrent.Future

object EpisodeController {

  def getEpisodeFile(id: EpisodeId): RIO[DataSource, HttpEntity.Default] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      file <-
        ZIO
          .succeed(
            StaticConfig.audioPath
              .resolve(episode.podcastId.unwrap.toString)
              .resolve(s"${episode.externalSource}.mp3")
              .toFile
          )
          .filterOrFail(_.exists)(HttpError(StatusCodes.NotFound))
    } yield HttpEntity.Default(
      MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
      file.length,
      FileIO.fromPath(file.toPath)
    )

  def getEpisodeFileOnDemand(
      episodesDownloading: Ref.Synchronized[Map[EpisodeId, Promise[Throwable, File]]]
  )(id: EpisodeId): RIO[DataSource, HttpEntity.Default] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      podcast <- PodcastDao.get(episode.podcastId).someOrFail(HttpError(StatusCodes.NotFound))
      _       <- ZIO.logInfo(s"Requested episode '${episode.title}' on demand")
      result <- podcast.sourceType match {
                  case SourceType.YouTube =>
                    getEpisodeFileOnDemandYouTube(episodesDownloading)(episode)

                  case SourceType.Directory =>
                    val mediaPath = Paths.get(episode.externalSource)
                    val mediaFile = mediaPath.toFile

                    if (!mediaFile.exists() || mediaFile.length == 0)
                      ZIO.fail(HttpError(StatusCodes.NotFound))
                    else
                      ZIO.succeed(
                        HttpEntity.Default(
                          MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
                          mediaFile.length,
                          FileIO.fromPath(mediaPath)
                        )
                      )
                }
    } yield result

  def getEpisodeFileOnDemandYouTube(
      episodesDownloading: Ref.Synchronized[Map[EpisodeId, Promise[Throwable, File]]]
  )(episode: Episode.Model): RIO[DataSource, HttpEntity.Default] =
    for {
      config <- ConfigurationDao.getPrimary
      promiseMap <- episodesDownloading.updateAndGetZIO { downloadMap =>
                      downloadMap.get(episode.id) match {
                        case None =>
                          for {
                            p <- Promise.make[Throwable, File]
                            _ <- YouTubeDL
                                   .download(episode.podcastId, episode.externalSource, config.downloaderPath)
                                   .onExit { e =>
                                     e.toEither.fold(p.fail, p.succeed) *>
                                       episodesDownloading.updateAndGetZIO(m => ZIO.succeed(m - episode.id))
                                   }
                                   .forkDaemon
                          } yield downloadMap + (episode.id -> p)

                        case Some(_) => ZIO.succeed(downloadMap)
                      }
                    }
      mediaFile <- promiseMap(episode.id).await
      _         <- EpisodeDao.updateMediaFile(episode.id, Some(mediaFile.getName))
      _ <- ZIO.when(mediaFile.length == 0) {
             ZIO.fail(HttpError(StatusCodes.NotFound))
           }
    } yield HttpEntity.Default(
      MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
      mediaFile.length,
      FileIO.fromPath(mediaFile.toPath)
    )

  def getThumbnail(id: EpisodeId): RIO[DataSource, Source[ByteString, Future[IOResult]]] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      result <- episode.imagePath.map(_.toFile) match {
                  case Some(imageFile) if imageFile.exists() =>
                    ZIO.succeed(FileIO.fromPath(imageFile.toPath))

                  case _ =>
                    Option(getClass.getResource("/question.png")).flatMap(ResourceFile.apply) match {
                      case None => ZIO.fail(HttpError(StatusCodes.InternalServerError))
                      case Some(resource) =>
                        ZIO.succeed(StreamConverters.fromInputStream(() => resource.url.openStream()))
                    }
                }
    } yield result

}
