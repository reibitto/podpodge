package podpodge.controllers

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, MediaTypes, StatusCodes, _ }
import akka.stream.scaladsl.FileIO
import io.circe.syntax._
import podpodge.db.Podcast
import podpodge.db.dao.{ EpisodeDao, PodcastDao }
import podpodge.http.HttpError
import podpodge.types.{ EpisodeId, PodcastId }
import podpodge.youtube.YouTubeClient
import podpodge.{ rss, Config, DownloadRequest, RssFormat }
import sttp.client.httpclient.zio.SttpClient
import sttp.client.{ asPath, basicRequest }
import sttp.model.Uri
import zio._
import zio.blocking.Blocking
import zio.logging.{ log, Logging }

object PodcastController {
  def getPodcast(id: PodcastId.Type): Task[ToResponseMarshallable] =
    for {
      podcast <- PodcastDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
    } yield HttpEntity(ContentTypes.`text/xml(UTF-8)`, podcast.asJson.spaces2)

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
      (mediaType, file) = podcast.image match {
                            case Some(imageName) =>
                              (MediaTypes.`image/jpeg`, Config.coversPath.resolve(imageName))
                            case None            =>
                              (MediaTypes.`image/png`, Config.defaultAssetsPath.resolve("question.png"))
                          }
    } yield HttpEntity.Default(mediaType, file.toFile.length, FileIO.fromPath(file))

  def getEpisodeThumbnail(id: EpisodeId.Type): Task[ToResponseMarshallable] =
    for {
      episode          <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      (mediaType, file) = episode.image match {
                            case Some(imageName) =>
                              (MediaTypes.`image/jpeg`, Config.thumbnailsPath.resolve(imageName))
                            case None            =>
                              (MediaTypes.`image/png`, Config.defaultAssetsPath.resolve("question.png"))
                          }
    } yield HttpEntity.Default(mediaType, file.toFile.length, FileIO.fromPath(file))

  def getPodcastRss(id: PodcastId.Type): Task[ToResponseMarshallable] =
    for {
      podcast  <- PodcastDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      episodes <- EpisodeDao.listByPodcast(id)
    } yield HttpEntity(
      ContentTypes.`text/xml(UTF-8)`,
      RssFormat.encode(rss.Podcast.fromDB(podcast, episodes)).toString()
    )

  def create(videoIdParam: String): RIO[SttpClient with Blocking, ToResponseMarshallable] =
    for {
      videoIds             <- UIO(videoIdParam.split(',').toList).filterOrFail(_.nonEmpty)(HttpError(StatusCodes.BadRequest))
      playlists            <- YouTubeClient.listPlaylists(videoIds, Config.apiKey).runCollect
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
    } yield HttpEntity(ContentTypes.`application/json`, podcasts.asJson.spaces2)

  def checkForUpdates(
    id: PodcastId.Type,
    downloadQueue: Queue[DownloadRequest]
  ): RIO[SttpClient with Blocking with Logging, ToResponseMarshallable] =
    for {
      podcast           <- PodcastDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      externalSources   <- EpisodeDao.listExternalSource.map(_.toSet)
      playlistItemStream = YouTubeClient.listPlaylistItems(podcast.externalSource, Config.apiKey)
      _                 <- playlistItemStream
                             .filter(item => !externalSources.contains(item.snippet.resourceId.videoId))
                             .foreach { item =>
                               log.trace(s"Putting '${item.snippet.title}' (${item.snippet.resourceId.videoId}) in download queue") *>
                                 downloadQueue.offer(DownloadRequest(id, item))
                             }
    } yield StatusCodes.OK
}
