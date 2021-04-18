package podpodge

import podpodge.db.Episode
import podpodge.db.dao.EpisodeDao
import podpodge.types._
import sttp.client._
import sttp.client.httpclient.zio.SttpClient
import sttp.model.Uri
import zio.blocking.Blocking
import zio.duration._
import zio.logging.{ log, Logging }
import zio.stream.ZStream
import zio.{ Has, Queue, URIO, ZIO }

import java.sql.Connection
import java.time.{ Instant, ZoneOffset }

object DownloadWorker {
  def make(queue: Queue[CreateEpisodeRequest]): URIO[Logging with Has[Connection] with Blocking with SttpClient, Unit] =
    ZStream
      .fromQueue(queue)
      .foreach { request =>
        val create = request match {
          case r: CreateEpisodeRequest.YouTube => createEpisodeYouTube(r)
          case r: CreateEpisodeRequest.File    => createEpisodeFile(r)
        }

        create.absorb.tapError { t =>
          log.throwable(s"Error creating episode for $request", t)
        }.ignore
      }

  def createEpisodeYouTube(
    request: CreateEpisodeRequest.YouTube
  ): ZIO[SttpClient with Has[Connection] with Blocking, Throwable, Unit] = {
    val videoId = request.playlistItem.snippet.resourceId.videoId

    for {
      episode <- EpisodeDao.create(
                   Episode(
                     EpisodeId.empty,
                     request.podcastId,
                     videoId,
                     videoId,
                     request.playlistItem.snippet.title,
                     request.playlistItem.snippet.publishedAt, // TODO: Add config option to select `request.playlistItem.contentDetails.videoPublishedAt` here
                     None,
                     None,
                     0.seconds                                 // TODO: Calculate duration
                   )
                 )
      _       <- ZIO.foreach_(request.playlistItem.snippet.thumbnails.highestRes) { thumbnail =>
                   for {
                     uri <- ZIO.fromEither(Uri.parse(thumbnail.url)).catchAll(ZIO.dieMessage(_))
                     req  = basicRequest
                              .get(uri)
                              .response(
                                asPath(
                                  StaticConfig.thumbnailsPath
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
    } yield ()
  }

  def createEpisodeFile(
    request: CreateEpisodeRequest.File
  ): ZIO[SttpClient with Has[Connection] with Blocking, Throwable, Unit] =
    for {
      _ <- EpisodeDao.create(
             Episode(
               EpisodeId.empty,
               request.podcastId,
               request.file.getCanonicalPath,
               request.file.getPath,
               request.file.getName,
               Instant.ofEpochMilli(request.file.lastModified()).atOffset(ZoneOffset.UTC),
               None,
               None,
               0.seconds // TODO: Calculate duration
             )
           )
    } yield ()
}
