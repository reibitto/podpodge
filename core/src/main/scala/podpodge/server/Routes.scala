package podpodge.server

import akka.http.scaladsl.server.Route
import podpodge.{CreateEpisodeRequest, Env}
import podpodge.controllers.{ConfigurationController, EpisodeController, PodcastController}
import podpodge.db.Podcast.Model
import podpodge.db.dao.ConfigurationDao
import podpodge.db.patch.PatchConfiguration
import podpodge.db.{Configuration, Podcast}
import podpodge.http.ApiError
import podpodge.types._
import sttp.capabilities.akka.AkkaStreams
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.{Promise, Queue, Ref, Runtime}

import java.io.File
import scala.concurrent.Future
import scala.xml.Elem

object Routes extends TapirSupport with TapirCodecEnumeratum {
  val listPodcastsEndpoint: Endpoint[Unit, Unit, ApiError, List[Model], Any] =
    endpoint
      .in("podcasts")
      .errorOut(apiError)
      .out(jsonBody[List[Podcast.Model]])
      .description("List all the podcasts currently registered.")

  val getPodcastEndpoint =
    endpoint
      .in("podcast" / path[PodcastId]("podcastId"))
      .errorOut(apiError)
      .out(jsonBody[Podcast.Model])
      .description("Get a single podcast by its ID.")

  val rssEndpoint =
    endpoint.get
      .in("podcast" / path[PodcastId]("podcastId") / "rss")
      .errorOut(apiError)
      .out(xmlBody[Elem])
      .description("Get the RSS feed for the specified podcast.")

  val checkForUpdatesAllEndpoint =
    endpoint.post
      .in("podcasts" / "check")
      .errorOut(apiError)
      .out(statusCode(StatusCode.Ok))
      .description("Checks for new episodes for all registered podcasts.")

  val checkForUpdatesEndpoint =
    endpoint.post
      .in("podcast" / path[PodcastId]("podcastId") / "check")
      .errorOut(apiError)
      .out(statusCode(StatusCode.Ok))
      .description("Checks for new episodes for the specified podcast.")

  val createPodcastEndpoint =
    endpoint.post
      .in("podcast" / path[SourceType]("sourceType"))
      .in(query[List[String]]("playlistId"))
      .errorOut(apiError)
      .out(jsonBody[List[Podcast.Model]])
      .description("Creates Podcast feeds for the specified YouTube playlist IDs.")

  val getConfigEndpoint =
    endpoint.get
      .in("configuration")
      .errorOut(apiError)
      .out(jsonBody[Configuration.Model])
      .description("Get Podpodge configuration")

  val updateConfigEndpoint =
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

  val coverEndpoint =
    endpoint.get
      .in("cover" / path[PodcastId]("podcastId"))
      .errorOut(apiError)
      .out(streamBinaryBody(AkkaStreams)(imageJpegCodec))

  val thumbnailEndpoint =
    endpoint.get
      .in("thumbnail" / path[EpisodeId]("episodeId"))
      .errorOut(apiError)
      .out(streamBinaryBody(AkkaStreams)(imageJpegCodec))

  def make(
    downloadQueue: Queue[CreateEpisodeRequest],
    episodesDownloading: Ref.Synchronized[Map[EpisodeId, Promise[Throwable, File]]]
  )(implicit runtime: Runtime[Env]): Route = {
    import akka.http.scaladsl.server.Directives._

    implicit val interpreter: AkkaHttpServerInterpreter =
      AkkaHttpServerInterpreter()(scala.concurrent.ExecutionContext.Implicits.global)

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
      swaggerRoute
  }

  def swaggerRoute(implicit interpreter: AkkaHttpServerInterpreter) =
    interpreter.toRoute(
      SwaggerInterpreter().fromEndpoints[Future](
        List(
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
        "0.2.0"
      )
    )

}
