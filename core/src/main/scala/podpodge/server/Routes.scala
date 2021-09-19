package podpodge.server

import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import podpodge.CreateEpisodeRequest
import podpodge.controllers.{ ConfigurationController, EpisodeController, PodcastController }
import podpodge.db.Podcast.Model
import podpodge.db.dao.ConfigurationDao
import podpodge.db.patch.PatchConfiguration
import podpodge.db.{ Configuration, Podcast }
import podpodge.http.ApiError
import podpodge.types._
import sttp.capabilities.akka.AkkaStreams
import sttp.model.{ Header, MediaType, StatusCode }
import sttp.tapir._
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.swagger.akkahttp.SwaggerAkka
import zio.{ Promise, Queue, RefM }

import java.io.File
import scala.xml.Elem

object Routes extends TapirSupport with TapirCodecEnumeratum {
  val listPodcastsEndpoint: Endpoint[Unit, ApiError, List[Podcast.Model], Any] =
    endpoint
      .in("podcasts")
      .errorOut(apiError)
      .out(jsonBody[List[Podcast.Model]])
      .description("List all the podcasts currently registered.")

  val getPodcastEndpoint: Endpoint[PodcastId, ApiError, Podcast.Model, Any] =
    endpoint
      .in("podcast" / path[PodcastId]("podcastId"))
      .errorOut(apiError)
      .out(jsonBody[Podcast.Model])
      .description("Get a single podcast by its ID.")

  val rssEndpoint: Endpoint[PodcastId, ApiError, Elem, Any] =
    endpoint.get
      .in("podcast" / path[PodcastId]("podcastId") / "rss")
      .errorOut(apiError)
      .out(xmlBody[Elem])
      .description("Get the RSS feed for the specified podcast.")

  val checkForUpdatesAllEndpoint: Endpoint[Unit, ApiError, Unit, Any] =
    endpoint.post
      .in("podcasts" / "check")
      .errorOut(apiError)
      .out(statusCode(StatusCode.Ok))
      .description("Checks for new episodes for all registered podcasts.")

  val checkForUpdatesEndpoint: Endpoint[PodcastId, ApiError, Unit, Any] =
    endpoint.post
      .in("podcast" / path[PodcastId]("podcastId") / "check")
      .errorOut(apiError)
      .out(statusCode(StatusCode.Ok))
      .description("Checks for new episodes for the specified podcast.")

  val createPodcastEndpoint: Endpoint[(SourceType, List[String]), ApiError, List[Model], Any] =
    endpoint.post
      .in("podcast" / path[SourceType]("sourceType"))
      .in(query[List[String]]("playlistId"))
      .errorOut(apiError)
      .out(jsonBody[List[Podcast.Model]])
      .description("Creates Podcast feeds for the specified YouTube playlist IDs.")

  val getConfigEndpoint: Endpoint[Unit, ApiError, Configuration.Model, Any] =
    endpoint.get
      .in("configuration")
      .errorOut(apiError)
      .out(jsonBody[Configuration.Model])
      .description("Get Podpodge configuration")

  val updateConfigEndpoint: Endpoint[PatchConfiguration, ApiError, Configuration.Model, Any] =
    endpoint.patch
      .in("configuration")
      .in(
        jsonBody[PatchConfiguration].example(
          PatchConfiguration(
            Tristate.Some(YouTubeApiKey("YOUR_API_KEY")),
            Tristate.Some(ServerHost("localhost")),
            Tristate.Some(ServerPort.makeUnsafe(80)),
            Tristate.Some(ServerScheme("http")),
            Tristate.Some(DownloaderPath("youtube-dl"))
          )
        )
      )
      .errorOut(apiError)
      .out(jsonBody[Configuration.Model])
      .description(
        "Updates Podpodge configuration. Pass `null` to clear out a field. If you want a field to remain unchanged, simply leave the field out from the JSON body completely."
      )

  val coverEndpoint: Endpoint[PodcastId, ApiError, Source[ByteString, Any], AkkaStreams] =
    endpoint.get
      .in("cover" / path[PodcastId]("podcastId"))
      .errorOut(apiError)
      .out(streamBinaryBody(AkkaStreams))
      .out(header(Header.contentType(MediaType.ImageJpeg)))

  val thumbnailEndpoint: Endpoint[EpisodeId, ApiError, Source[ByteString, Any], AkkaStreams] =
    endpoint.get
      .in("thumbnail" / path[EpisodeId]("episodeId"))
      .errorOut(apiError)
      .out(streamBinaryBody(AkkaStreams))
      .out(header(Header.contentType(MediaType.ImageJpeg)))

  def make(
    downloadQueue: Queue[CreateEpisodeRequest],
    episodesDownloading: RefM[Map[EpisodeId, Promise[Throwable, File]]]
  ): Route = {
    import akka.http.scaladsl.server.Directives._

    listPodcastsEndpoint.toZRoute(_ => PodcastController.listPodcasts) ~
      getPodcastEndpoint.toZRoute(PodcastController.getPodcast) ~
      rssEndpoint.toZRoute(PodcastController.getPodcastRss) ~
      checkForUpdatesAllEndpoint.toZRoute(_ => PodcastController.checkForUpdatesAll(downloadQueue)) ~
      checkForUpdatesEndpoint.toZRoute(PodcastController.checkForUpdates(downloadQueue)) ~
      createPodcastEndpoint.toZRoute((PodcastController.create _).tupled(_)) ~
      getConfigEndpoint.toZRoute(_ => ConfigurationController.getPrimary) ~
      updateConfigEndpoint.toZRoute({ patch =>
        for {
          defaultConfiguration <- ConfigurationDao.getPrimary
          result               <- ConfigurationController.patch(defaultConfiguration.id, patch)
        } yield result
      }) ~
      coverEndpoint.toZRoute(PodcastController.getPodcastCover) ~
      thumbnailEndpoint.toZRoute(EpisodeController.getThumbnail) ~
      RawRoutes.all(episodesDownloading) ~
      new SwaggerAkka(openApiDocs).routes
  }

  def openApiDocs: String = {
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.tapir.openapi.circe.yaml._

    OpenAPIDocsInterpreter()
      .toOpenAPI(
        Seq(
          listPodcastsEndpoint,
          getPodcastEndpoint,
          rssEndpoint,
          checkForUpdatesAllEndpoint,
          checkForUpdatesEndpoint,
          createPodcastEndpoint,
          getConfigEndpoint,
          updateConfigEndpoint
        ),
        "Podpodge Docs",
        "0.1.0"
      )
      .toYaml
  }

}
