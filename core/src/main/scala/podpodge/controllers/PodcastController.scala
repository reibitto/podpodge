package podpodge.controllers

import akka.http.scaladsl.model.{ HttpEntity, MediaTypes, StatusCodes }
import akka.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import akka.stream.scaladsl.{ FileIO, StreamConverters }
import podpodge.db.Podcast
import podpodge.db.dao.{ EpisodeDao, PodcastDao }
import podpodge.http.{ ApiError, HttpError }
import podpodge.types.PodcastId
import podpodge.youtube.YouTubeClient
import podpodge.{ rss, Config, DownloadRequest, RssFormat }
import sttp.client.httpclient.zio.SttpClient
import sttp.client.{ asPath, basicRequest }
import sttp.model.Uri
import zio._
import zio.blocking.Blocking
import zio.logging.{ log, Logging }

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

  def create(playlistIds: List[String]): RIO[SttpClient with Blocking, List[Podcast.Model]] =
    for {
      playlists            <- YouTubeClient.listPlaylists(playlistIds, Config.apiKey).runCollect
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

  def checkForUpdatesAll(downloadQueue: Queue[DownloadRequest]): RIO[SttpClient with Blocking with Logging, Unit] =
    for {
      podcasts        <- PodcastDao.list
      externalSources <- EpisodeDao.listExternalSource.map(_.toSet)
      _               <- ZIO.foreach_(podcasts) { podcast =>
                           enqueueDownload(downloadQueue)(podcast, externalSources)
                         }
    } yield ()

  def checkForUpdates(
    downloadQueue: Queue[DownloadRequest]
  )(id: PodcastId): RIO[SttpClient with Blocking with Logging, Unit] =
    for {
      podcast         <- PodcastDao.get(id).someOrFail(ApiError.NotFound(s"Podcast $id does not exist."))
      externalSources <- EpisodeDao.listExternalSource.map(_.toSet)
      _               <- enqueueDownload(downloadQueue)(podcast, externalSources)
    } yield ()

  private def enqueueDownload(
    downloadQueue: Queue[DownloadRequest]
  )(podcast: Podcast.Model, excludeExternalSources: Set[String]): RIO[Logging with SttpClient with Blocking, Unit] =
    // TODO: Update lastCheckDate here. Will definitely need it for the cron schedule feature.
    YouTubeClient
      .listPlaylistItems(podcast.externalSource, Config.apiKey)
      .filterNot(item => excludeExternalSources.contains(item.snippet.resourceId.videoId))
      .foreach { item =>
        log.trace(s"Putting '${item.snippet.title}' (${item.snippet.resourceId.videoId}) in download queue") *>
          downloadQueue.offer(DownloadRequest(podcast.id, item))
      }
      .tap(_ => log.info(s"Done checking for new podcasts for Podcast ${podcast.id}"))

}
