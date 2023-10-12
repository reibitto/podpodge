package podpodge.server

import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.{PathMatcher1, Route}
import org.apache.pekko.http.scaladsl.server.Directives.*
import podpodge.controllers.EpisodeController
import podpodge.http.PekkoHttp.*
import podpodge.types.{EpisodeId, PodcastId}
import podpodge.Env
import zio.{Promise, Ref, Runtime}

import java.io.File
import scala.concurrent.duration.*

// Routes that are using plain pekko-http rather than through tapir's interface.
object RawRoutes {

  val PodcastIdPart: PathMatcher1[PodcastId] = LongNumber.map(PodcastId(_))
  val EpisodeIdPart: PathMatcher1[EpisodeId] = LongNumber.map(EpisodeId(_))

  def all(
      episodesDownloading: Ref.Synchronized[Map[EpisodeId, Promise[Throwable, File]]]
  )(implicit runtime: Runtime[Env]): Route =
    pathSingleSlash {
      redirect("/docs", StatusCodes.TemporaryRedirect)
    } ~
      path("episode" / EpisodeIdPart / "file") { id =>
        // TODO: Make this configurable.
        // The timeout is set to ridiculously long value because downloading a YouTube video can take a long time, and this
        // route can also initiate a download if the media file doesn't already exist.
        withRequestTimeout(1.hour) {
          withRangeSupport {
            get {
              EpisodeController.getEpisodeFileOnDemand(episodesDownloading)(id)
            }
          }
        }
      }

}
