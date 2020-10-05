package podpodge.controllers

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ HttpEntity, MediaTypes, StatusCodes, _ }
import akka.stream.scaladsl.FileIO
import podpodge.db.Podcast
import podpodge.db.dao.{ EpisodeDao, PodcastDao }
import podpodge.http.{ ApiError, HttpError }
import podpodge.types.{ EpisodeId, PodcastId }
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
  def getPodcast(id: PodcastId.Type): Task[Podcast.Model] =
    PodcastDao.get(id).someOrFail(ApiError.NotFound(s"Podcast $id does not exist."))

  def listPodcasts: Task[List[Podcast.Model]] = PodcastDao.list

  def getEpisodeFile(id: EpisodeId.Type): Task[ToResponseMarshallable] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      file    <- UIO(Config.audioPath.resolve(s"${episode.externalSource}.mp3").toFile)
                   .filterOrFail(_.exists)(HttpError(StatusCodes.NotFound))
    } yield HttpEntity.Default(
      MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
      file.length,
      FileIO.fromPath(file.toPath)
    )

  def getPodcastCover(id: PodcastId.Type): Task[ToResponseMarshallable] =
    for {
      podcast          <- PodcastDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      (mediaType, path) = podcast.image match {
                            case Some(imageName) =>
                              (MediaTypes.`image/jpeg`, Config.coversPath.resolve(imageName))
                            case None            =>
                              (MediaTypes.`image/png`, Config.defaultAssetsPath.resolve("question.png"))
                          }
    } yield HttpEntity.Default(mediaType, path.toFile.length, FileIO.fromPath(path))

  def getEpisodeThumbnail(id: EpisodeId.Type): Task[ToResponseMarshallable] =
    for {
      episode          <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      (mediaType, path) = episode.image match {
                            case Some(imageName) =>
                              (MediaTypes.`image/jpeg`, Config.thumbnailsPath.resolve(imageName))
                            case None            =>
                              (MediaTypes.`image/png`, Config.defaultAssetsPath.resolve("question.png"))
                          }
    } yield HttpEntity.Default(mediaType, path.toFile.length, FileIO.fromPath(path))

  def getPodcastRss(id: PodcastId.Type): Task[Elem] =
    for {
      podcast  <- PodcastDao.get(id).someOrFail(ApiError.NotFound(s"Podcast $id does not exist."))
      episodes <- EpisodeDao.listByPodcast(id)
    } yield RssFormat.encode(rss.Podcast.fromDB(podcast, episodes))

  def create(playlistIdsParam: String): RIO[SttpClient with Blocking, List[Podcast.Model]] =
    for {
      playlistIds          <- UIO(playlistIdsParam.split(',').toList).filterOrFail(_.nonEmpty)(HttpError(StatusCodes.BadRequest))
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
  )(id: PodcastId.Type): RIO[SttpClient with Blocking with Logging, Unit] =
    for {
      podcast         <- PodcastDao.get(id).someOrFail(ApiError.NotFound(s"Podcast $id does not exist."))
      externalSources <- EpisodeDao.listExternalSource.map(_.toSet)
      _               <- enqueueDownload(downloadQueue)(podcast, externalSources)
    } yield ()

  private def enqueueDownload(
    downloadQueue: Queue[DownloadRequest]
  )(podcast: Podcast.Model, excludeExternalSources: Set[String]): RIO[Logging with SttpClient with Blocking, Unit] =
    YouTubeClient
      .listPlaylistItems(podcast.externalSource, Config.apiKey)
      .filterNot(item => excludeExternalSources.contains(item.snippet.resourceId.videoId))
      .foreach { item =>
        log.trace(s"Putting '${item.snippet.title}' (${item.snippet.resourceId.videoId}) in download queue") *>
          downloadQueue.offer(DownloadRequest(podcast.id, item))
      }

}
