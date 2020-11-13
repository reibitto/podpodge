package podpodge.controllers

import java.nio.file.{ Files, Paths }

import akka.http.scaladsl.model.{ HttpEntity, MediaTypes, StatusCodes }
import akka.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import akka.stream.scaladsl.{ FileIO, StreamConverters }
import podpodge.db.Podcast
import podpodge.db.dao.{ EpisodeDao, PodcastDao }
import podpodge.http.{ ApiError, HttpError }
import podpodge.types.{ PodcastId, SourceType }
import podpodge.youtube.YouTubeClient
import podpodge.{ rss, Config, CreateEpisodeRequest, RssFormat }
import sttp.client.httpclient.zio.SttpClient
import sttp.client.{ asPath, basicRequest }
import sttp.model.Uri
import zio._
import zio.blocking.Blocking
import zio.logging.{ log, Logging }
import zio.stream.ZStream

import scala.xml.Elem

object PodcastController {
  def getPodcast(id: PodcastId): Task[Podcast.Model] =
    PodcastDao.get(id).someOrFail(ApiError.NotFound(s"Podcast $id does not exist."))

  def listPodcasts: Task[List[Podcast.Model]] = PodcastDao.list

  def getPodcastRss(id: PodcastId): Task[Elem] =
    for {
      podcast  <- PodcastDao.get(id).someOrFail(ApiError.NotFound(s"Podcast $id does not exist."))
      episodes <- EpisodeDao.listByPodcast(id)
    } yield RssFormat.encode(rss.Podcast.fromDB(podcast, episodes))

  def getPodcastCover(id: PodcastId): Task[HttpEntity.Default] =
    for {
      podcast <- PodcastDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      result  <- podcast.imagePath.map(_.toFile) match {
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

  def create(sourceType: SourceType, sources: List[String]): RIO[SttpClient with Blocking, List[Podcast.Model]] =
    sourceType match {
      case SourceType.YouTube =>
        for {
          playlists            <- YouTubeClient.listPlaylists(sources, Config.apiKey).runCollect
          podcasts             <- PodcastDao.createAll(playlists.toList.map(Podcast.fromPlaylist))
          podcastsWithPlaylists = podcasts.zip(playlists)
          _                    <- ZIO.foreach_(podcastsWithPlaylists) { case (podcast, playlist) =>
                                    ZIO.foreach_(playlist.snippet.thumbnails.highestRes) { thumbnail =>
                                      for {
                                        uri                 <- ZIO.fromEither(Uri.parse(thumbnail.url)).catchAll(ZIO.dieMessage(_))
                                        req                  = basicRequest.get(uri).response(asPath(Config.coversPath.resolve(s"${podcast.id}.jpg")))
                                        downloadedThumbnail <- SttpClient.send(req)
                                        _                   <- ZIO.whenCase(downloadedThumbnail.body) { case Right(_) =>
                                                                 PodcastDao.updateImage(podcast.id, Some(s"${podcast.id}.jpg"))
                                                               }
                                      } yield ()
                                    }
                                  }
        } yield podcasts

      case SourceType.Directory =>
        val (errors, results) = sources.map { source =>
          Podcast.fromDirectory(Paths.get(source))
        }.partitionMap(identity)

        if (errors.nonEmpty) {
          ZIO.fail(ApiError.BadRequest(errors.mkString("\n")))
        } else {
          PodcastDao.createAll(results)
        }
    }

  def checkForUpdatesAll(downloadQueue: Queue[CreateEpisodeRequest]): RIO[SttpClient with Blocking with Logging, Unit] =
    for {
      podcasts        <- PodcastDao.list
      externalSources <- EpisodeDao.listExternalSource.map(_.toSet)
      _               <- ZIO.foreach_(podcasts) { podcast =>
                           enqueueDownload(downloadQueue)(podcast, externalSources)
                         }
    } yield ()

  def checkForUpdates(
    downloadQueue: Queue[CreateEpisodeRequest]
  )(id: PodcastId): RIO[SttpClient with Blocking with Logging, Unit] =
    for {
      podcast         <- PodcastDao.get(id).someOrFail(ApiError.NotFound(s"Podcast $id does not exist."))
      externalSources <- EpisodeDao.listExternalSource.map(_.toSet)
      _               <- enqueueDownload(downloadQueue)(podcast, externalSources)
    } yield ()

  private def enqueueDownload(
    downloadQueue: Queue[CreateEpisodeRequest]
  )(podcast: Podcast.Model, excludeExternalSources: Set[String]): RIO[Logging with SttpClient with Blocking, Unit] =
    podcast.sourceType match {
      case SourceType.YouTube   => enqueueDownloadYouTube(downloadQueue)(podcast, excludeExternalSources)
      case SourceType.Directory => enqueueDownloadFile(downloadQueue)(podcast, excludeExternalSources)
    }

  private def enqueueDownloadFile(
    downloadQueue: Queue[CreateEpisodeRequest]
  )(podcast: Podcast.Model, excludeExternalSources: Set[String]): RIO[Logging, Unit] = {
    import podpodge.io.FileExtensions._

    val excludeExternalPaths = excludeExternalSources.map(s => Paths.get(s).normalize().toFile)

    ZStream
      .fromJavaStream(Files.walk(Paths.get(podcast.externalSource)))
      .map(_.normalize().toFile)
      .filterNot(item => excludeExternalPaths.contains(item))
      .filter { s =>
        s.extension match {
          case Some(ext) if Set("mp3", "ogg").contains(ext.toLowerCase) =>
            true
          case _                                                        => false
        }
      }
      .foreach { item =>
        log.trace(s"Putting '${item}' in download queue") *>
          downloadQueue.offer(CreateEpisodeRequest.File(podcast.id, item))
      }
      .tap(_ => log.info(s"Done checking for new episode files for Podcast ${podcast.id}"))
  }

  private def enqueueDownloadYouTube(
    downloadQueue: Queue[CreateEpisodeRequest]
  )(podcast: Podcast.Model, excludeExternalSources: Set[String]): RIO[Logging with SttpClient with Blocking, Unit] =
    // TODO: Update lastCheckDate here. Will definitely need it for the cron schedule feature.
    YouTubeClient
      .listPlaylistItems(podcast.externalSource, Config.apiKey)
      .filterNot(item => excludeExternalSources.contains(item.snippet.resourceId.videoId))
      .foreach { item =>
        log.trace(s"Putting '${item.snippet.title}' (${item.snippet.resourceId.videoId}) in download queue") *>
          downloadQueue.offer(CreateEpisodeRequest.YouTube(podcast.id, item))
      }
      .tap(_ => log.info(s"Done checking for new YouTube episodes for Podcast ${podcast.id}"))

}
