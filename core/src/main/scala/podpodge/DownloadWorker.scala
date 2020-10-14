package podpodge

import podpodge.db.Episode
import podpodge.db.dao.EpisodeDao
import podpodge.types._
import podpodge.youtube.{ PlaylistItem, YouTubeDL }
import sttp.client.httpclient.zio.SttpClient
import sttp.client._
import sttp.model.Uri
import zio.blocking.Blocking
import zio.duration._
import zio.logging.{ log, Logging }
import zio.stream.ZStream
import zio.{ Queue, URIO, ZIO }

object DownloadWorker {
  def make(queue: Queue[DownloadRequest]): URIO[Logging with Blocking with SttpClient, Unit] =
    ZStream
      .fromQueue(queue)
      .foreach { request =>
        val videoId = request.playlistItem.snippet.resourceId.videoId

        (for {
          downloadedFile <- YouTubeDL.download(request.podcastId, videoId)
          episode        <- EpisodeDao.create(
                              Episode(
                                EpisodeId.empty,
                                request.podcastId,
                                videoId,
                                videoId,
                                request.playlistItem.snippet.title,
                                request.playlistItem.snippet.publishedAt, // TODO: Add config option to select `request.playlistItem.contentDetails.videoPublishedAt` here
                                None,
                                Some(downloadedFile.getName),
                                0.seconds                                 // TODO: Calculate duration
                              )
                            )
          _              <- ZIO.foreach_(request.playlistItem.snippet.thumbnails.highestRes) { thumbnail =>
                              for {
                                uri <- ZIO.fromEither(Uri.parse(thumbnail.url)).catchAll(ZIO.dieMessage(_))
                                req  = basicRequest
                                         .get(uri)
                                         .response(
                                           asPath(
                                             Config.thumbnailsPath
                                               .resolve(request.podcastId.unwrap.toString)
                                               .resolve(s"${episode.id}.jpg")
                                           )
                                         )

                                downloadedThumbnail <- SttpClient.send(req)
                                _                   <- ZIO.whenCase(downloadedThumbnail.body) { case Right(_) =>
                                                         EpisodeDao.updateImage(episode.id, Some(s"${episode.id}.jpg"))
                                                       }
                              } yield ()
                            }
        } yield ()).absorb.tapError { t =>
          log.throwable(s"Error downloading video '$videoId'", t)
        }.ignore
      }
      .tap(_ => log.info("Download task complete."))
}

final case class DownloadRequest(podcastId: PodcastId, playlistItem: PlaylistItem)
