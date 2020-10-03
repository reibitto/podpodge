package podpodge.server

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ PathMatcher1, Route }
import podpodge.DownloadRequest
import podpodge.controllers.PodcastController
import podpodge.http.AkkaHttp._
import podpodge.types.{ EpisodeId, PodcastId }
import zio.{ Queue, UIO }

object Routes {

  val PodcastIdPart: PathMatcher1[PodcastId.Type] = LongNumber.map(PodcastId(_))
  val EpisodeIdPart: PathMatcher1[EpisodeId.Type] = LongNumber.map(EpisodeId(_))

  def make(downloadQueue: Queue[DownloadRequest]): Route =
    pathSingleSlash {
      zioRoute(
        UIO(
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            "<html><body><h1>A frontend for Podpodge will be coming soon</h1></body></html>"
          )
        )
      )
    } ~
      path("podcast" / PodcastIdPart) { id =>
        get {
          PodcastController.getPodcast(id)
        }
      } ~
      path("podcast" / Segment) { videoIdPart =>
        post {
          PodcastController.create(videoIdPart)
        }
      } ~
      path("podcast" / PodcastIdPart / "rss") { id =>
        get {
          PodcastController.getPodcastRss(id)
        }
      } ~
      path("podcast" / PodcastIdPart / "check") { id =>
        post {
          PodcastController.checkForUpdates(id, downloadQueue)
        }
      } ~
      path("episode" / EpisodeIdPart / "file") { id =>
        get {
          withRangeSupport {
            PodcastController.getEpisodeFile(id)
          }
        }
      } ~
      path("cover" / PodcastIdPart) { id =>
        get {
          PodcastController.getPodcastCover(id)
        }
      } ~
      path("thumbnail" / EpisodeIdPart) { id =>
        get {
          PodcastController.getEpisodeThumbnail(id)
        }
      }

}
