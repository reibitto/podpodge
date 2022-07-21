package podpodge.controllers

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import akka.stream.IOResult
import akka.util.ByteString
import podpodge.*
import podpodge.config.Config
import podpodge.db.dao.{EpisodeDao, PodcastDao}
import podpodge.db.Podcast
import podpodge.db.Podcast.Model
import podpodge.http.{ApiError, HttpError, Sttp}
import podpodge.types.{PodcastId, SourceType}
import podpodge.youtube.YouTubeClient
import sttp.client3.*
import sttp.model.Uri
import zio.*
import zio.stream.ZStream

import java.nio.file.{Files, Paths}
import java.sql.SQLException
import javax.sql.DataSource
import scala.concurrent.Future
import scala.xml.Elem

object PodcastController {

  def getPodcast(id: PodcastId): RIO[DataSource, Model] =
    PodcastDao.get(id).someOrFail(ApiError.NotFound(s"Podcast $id does not exist."))

  def listPodcasts: ZIO[DataSource, SQLException, List[Model]] = PodcastDao.list

  def getPodcastRss(id: PodcastId): RIO[DataSource & Config, Elem] =
    for {
      config   <- config.get
      podcast  <- PodcastDao.get(id).someOrFail(ApiError.NotFound(s"Podcast $id does not exist."))
      episodes <- EpisodeDao.listByPodcast(id)
    } yield RssFormat.encode(rss.Podcast.fromDB(podcast, episodes, config))

  def getPodcastCover(
    id: PodcastId
  ): ZIO[DataSource, Exception, Source[ByteString, Future[IOResult]]] =
    for {
      podcast <- PodcastDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      result <- podcast.imagePath.map(_.toFile) match {
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

  def create(
    sourceType: SourceType,
    sources: List[String]
  ): RIO[Sttp & DataSource & Config, List[Podcast.Model]] =
    sourceType match {
      case SourceType.YouTube =>
        for {
          youTubeApiKey <- config.youTubeApiKey
          playlists     <- YouTubeClient.listPlaylists(sources, youTubeApiKey).runCollect
          _ <- ZIO.when(playlists.isEmpty) {
                 ZIO.fail(
                   ApiError.BadRequest(
                     "No playlists found. Are you sure you marked them as unlisted or public rather than private? Currently private playlists are not supported."
                   )
                 )
               }
          podcasts <- PodcastDao.createAll(playlists.toList.map(Podcast.fromPlaylist))
          podcastsWithPlaylists = podcasts.zip(playlists)
          _ <- ZIO.foreachDiscard(podcastsWithPlaylists) { case (podcast, playlist) =>
                 ZIO.foreachDiscard(playlist.snippet.thumbnails.highestRes) { thumbnail =>
                   for {
                     uri <- ZIO.fromEither(Uri.parse(thumbnail.url)).catchAll(ZIO.dieMessage(_))
                     req = basicRequest.get(uri).response(asPath(StaticConfig.coversPath.resolve(s"${podcast.id}.jpg")))
                     downloadedThumbnail <- Sttp.send(req)
                     _ <- ZIO.whenCase(downloadedThumbnail.body) { case Right(_) =>
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

  def checkForUpdatesAll(
    downloadQueue: Queue[CreateEpisodeRequest]
  ): RIO[Sttp & DataSource & Config, Unit] =
    for {
      podcasts        <- PodcastDao.list
      externalSources <- EpisodeDao.listExternalSource.map(_.toSet)
      _ <- ZIO.foreachDiscard(podcasts) { podcast =>
             enqueueDownload(downloadQueue)(podcast, externalSources)
           }
    } yield ()

  def checkForUpdates(
    downloadQueue: Queue[CreateEpisodeRequest]
  )(id: PodcastId): RIO[Sttp & DataSource & Config, Unit] =
    for {
      podcast         <- PodcastDao.get(id).someOrFail(ApiError.NotFound(s"Podcast $id does not exist."))
      externalSources <- EpisodeDao.listExternalSource.map(_.toSet)
      _               <- enqueueDownload(downloadQueue)(podcast, externalSources)
    } yield ()

  private def enqueueDownload(downloadQueue: Queue[CreateEpisodeRequest])(
    podcast: Podcast.Model,
    excludeExternalSources: Set[String]
  ): RIO[Sttp & Config, Unit] =
    podcast.sourceType match {
      case SourceType.YouTube   => enqueueDownloadYouTube(downloadQueue)(podcast, excludeExternalSources)
      case SourceType.Directory => enqueueDownloadFile(downloadQueue)(podcast, excludeExternalSources)
    }

  private def enqueueDownloadFile(
    downloadQueue: Queue[CreateEpisodeRequest]
  )(podcast: Podcast.Model, excludeExternalSources: Set[String]): RIO[Any, Unit] = {
    import podpodge.util.FileExtensions.*

    val excludeExternalPaths = excludeExternalSources.map(s => Paths.get(s).normalize().toFile)

    ZStream
      .fromJavaStream(Files.walk(Paths.get(podcast.externalSource)))
      .map(_.normalize().toFile)
      .filterNot(item => excludeExternalPaths.contains(item))
      .filter { s =>
        s.extension match {
          case Some(ext) if Set("mp3", "ogg", "m4a").contains(ext.toLowerCase) => true
          case _                                                               => false
        }
      }
      .foreach { item =>
        ZIO.logTrace(s"Putting '$item' in download queue") *>
          downloadQueue.offer(CreateEpisodeRequest.File(podcast.id, item))
      }
      .tap(_ => ZIO.logInfo(s"Done checking for new episode files for Podcast ${podcast.id}"))
  }

  private def enqueueDownloadYouTube(
    downloadQueue: Queue[CreateEpisodeRequest]
  )(
    podcast: Podcast.Model,
    excludeExternalSources: Set[String]
  ): RIO[Sttp & Config, Unit] = for {
    youTubeApiKey <- config.youTubeApiKey
    result <- // TODO: Update lastCheckDate here. Will definitely need it for the cron schedule feature.
      YouTubeClient
        .listPlaylistItems(podcast.externalSource, youTubeApiKey)
        .filterNot(item => excludeExternalSources.contains(item.snippet.resourceId.videoId))
        .foreach { item =>
          ZIO.logTrace(s"Putting '${item.snippet.title}' (${item.snippet.resourceId.videoId}) in download queue") *>
            downloadQueue.offer(CreateEpisodeRequest.YouTube(podcast.id, item))
        }
        .tap(_ => ZIO.logInfo(s"Done checking for new YouTube episodes for Podcast ${podcast.id}"))
  } yield result

}
