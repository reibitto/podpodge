package podpodge.server

import java.io.File

import akka.http.scaladsl.server.Route
import podpodge.CreateEpisodeRequest
import podpodge.controllers.PodcastController
import podpodge.db.Podcast
import podpodge.db.Podcast.Model
import podpodge.http.ApiError
import podpodge.types.{ EpisodeId, PodcastId, SourceType }
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum
import sttp.tapir.json.circe._
import sttp.tapir.swagger.akkahttp.SwaggerAkka
import zio.{ Promise, Queue, RefM }

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
      RawRoutes.all(episodesDownloading) ~
      new SwaggerAkka(openApiDocs).routes
  }

  def openApiDocs: String = {
    import sttp.tapir.docs.openapi._
    import sttp.tapir.openapi.circe.yaml._

    List(
      listPodcastsEndpoint,
      getPodcastEndpoint,
      rssEndpoint,
      checkForUpdatesAllEndpoint,
      checkForUpdatesEndpoint,
      createPodcastEndpoint
    ).toOpenAPI("Podpodge Docs", "0.1.0").toYaml
  }

}
