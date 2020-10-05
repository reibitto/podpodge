package podpodge.server

import akka.http.scaladsl.server.Route
import podpodge.DownloadRequest
import podpodge.controllers.PodcastController
import podpodge.db.Podcast
import podpodge.http.ApiError
import podpodge.types.PodcastId
import sttp.model.StatusCode
import sttp.tapir.{ Endpoint, _ }
import sttp.tapir.json.circe._
import sttp.tapir.swagger.akkahttp.SwaggerAkka
import zio.Queue

import scala.xml.Elem

object Routes extends TapirSupport {
  val listPodcastsEndpoint: Endpoint[Unit, ApiError, List[Podcast.Model], Any] =
    endpoint
      .in("podcasts")
      .errorOut(apiError)
      .out(jsonBody[List[Podcast.Model]])
      .description("List all the podcasts currently registered.")

  val getPodcastEndpoint: Endpoint[PodcastId.Type, ApiError, Podcast.Model, Any] =
    endpoint
      .in("podcast" / path[PodcastId.Type]("podcastId"))
      .errorOut(apiError)
      .out(jsonBody[Podcast.Model])
      .description("Get a single podcast by its ID.")

  val rssEndpoint: Endpoint[PodcastId.Type, ApiError, Elem, Any] =
    endpoint.get
      .in("podcast" / path[PodcastId.Type]("podcastId") / "rss")
      .errorOut(apiError)
      .out(xmlBody[Elem])
      .description("Get the RSS feed for the specified podcast.")

  val checkForUpdatesAllEndpoint: Endpoint[Unit, ApiError, Unit, Any] =
    endpoint.post
      .in("podcasts" / "check")
      .errorOut(apiError)
      .out(statusCode(StatusCode.Ok))
      .description("Checks for new episodes for all registered podcasts.")

  val checkForUpdatesEndpoint: Endpoint[PodcastId.Type, ApiError, Unit, Any] =
    endpoint.post
      .in("podcast" / path[PodcastId.Type]("podcastId") / "check")
      .errorOut(apiError)
      .out(statusCode(StatusCode.Ok))
      .description("Checks for new episodes for the specified podcast.")

  val createPodcastEndpoint: Endpoint[String, ApiError, List[Podcast.Model], Any] =
    endpoint.post
      .in("podcast" / path[String]("playlistIds"))
      .errorOut(apiError)
      .out(jsonBody[List[Podcast.Model]])
      .description(
        "Creates Podcast feeds for the specified YouTube playlist IDs. Note, you can use commas to input multiple playlist IDs."
      )

  def make(downloadQueue: Queue[DownloadRequest]): Route = {
    import akka.http.scaladsl.server.Directives._

    listPodcastsEndpoint.toZRoute(_ => PodcastController.listPodcasts) ~
      getPodcastEndpoint.toZRoute(PodcastController.getPodcast) ~
      rssEndpoint.toZRoute(PodcastController.getPodcastRss) ~
      checkForUpdatesAllEndpoint.toZRoute(_ => PodcastController.checkForUpdatesAll(downloadQueue)) ~
      checkForUpdatesEndpoint.toZRoute(PodcastController.checkForUpdates(downloadQueue)) ~
      createPodcastEndpoint.toZRoute(PodcastController.create) ~
      RawRoutes.all ~
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
    )
      .toOpenAPI("Podpodge Docs", "0.1.0")
      .toYaml
  }

}
