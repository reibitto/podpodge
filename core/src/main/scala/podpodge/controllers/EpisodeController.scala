package podpodge.controllers

import org.apache.pekko.http.scaladsl.model.{HttpEntity, MediaType}
import org.apache.pekko.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import org.apache.pekko.stream.scaladsl.{FileIO, Source, StreamConverters}
import org.apache.pekko.stream.IOResult
import org.apache.pekko.util.ByteString
import podpodge.db.dao.{ConfigurationDao, EpisodeDao, PodcastDao}
import podpodge.db.Episode
import podpodge.http.ApiError
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
      episode <- EpisodeDao.get(id).someOrFail(ApiError.NotFound(s"Episode $id does not exist."))
      file <-
        ZIO
          .succeed(
            StaticConfig.audioPath
              .resolve(episode.podcastId.unwrap.toString)
              .resolve(s"${episode.externalSource}.mp3")
              .toFile
          )
          .filterOrFail(_.exists)(ApiError.NotFound(s"Media file for episode $id does not exist."))
    } yield HttpEntity.Default(
      MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
      file.length,
      FileIO.fromPath(file.toPath)
    )

  def getEpisodeFileOnDemand(
      episodesDownloading: Ref.Synchronized[Map[EpisodeId, Promise[Throwable, File]]]
  )(id: EpisodeId): RIO[DataSource, HttpEntity.Default] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(ApiError.NotFound(s"Episode $id does not exist."))
      podcast <-
        PodcastDao.get(episode.podcastId).someOrFail(ApiError.NotFound(s"Podcast ${episode.podcastId} does not exist."))
      _ <- ZIO.logInfo(s"Requested episode '${episode.title}' on demand")
      result <- podcast.sourceType match {
                  case SourceType.YouTube =>
                    getEpisodeFileOnDemandYouTube(episodesDownloading)(episode)

                  case SourceType.Directory =>
                    val mediaPath = Paths.get(episode.externalSource)

                    ZIO.succeed(
                      HttpEntity.Default(
                        MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
                        mediaPath.toFile.length,
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
      // Uninterruptible because once the download fiber is forked, the map entry for it must
      // be committed too. Otherwise a request canceled at just the wrong moment could leave the
      // download running with no promise anyone can await.
      //
      // The critical section is just a fork and a map insert, so nothing here blocks long enough for this to be costly.
      promiseMap <- ZIO.uninterruptible {
                      episodesDownloading.updateAndGetZIO { downloadMap =>
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
                    }
      mediaFile <- promiseMap(episode.id).await
      _         <- EpisodeDao.updateMediaFile(episode.id, Some(mediaFile.getName))
    } yield HttpEntity.Default(
      MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
      mediaFile.length,
      FileIO.fromPath(mediaFile.toPath)
    )

  def getThumbnail(id: EpisodeId): RIO[DataSource, Source[ByteString, Future[IOResult]]] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(ApiError.NotFound(s"Episode $id does not exist."))
      result <- episode.imagePath.map(_.toFile) match {
                  case Some(imageFile) if imageFile.exists() =>
                    ZIO.succeed(FileIO.fromPath(imageFile.toPath))

                  case _ =>
                    Option(getClass.getResource("/question.png")).flatMap(ResourceFile.apply) match {
                      case None => ZIO.fail(ApiError.InternalError("Default thumbnail resource is missing."))
                      case Some(resource) =>
                        ZIO.succeed(StreamConverters.fromInputStream(() => resource.url.openStream()))
                    }
                }
    } yield result

}
