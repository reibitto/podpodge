package podpodge

import io.circe.{Decoder, Encoder}
import zio.prelude.*
import zio.prelude.Assertion.*

package object types {

  abstract class RichNewtype[A: Encoder: Decoder] extends Newtype[A] { self =>
    implicit val equiv: A <=> Type = Equivalence(wrap, unwrap)

    implicit val encoder: Encoder[Type] = implicitly[Encoder[A]].contramap(unwrap)
    implicit val decoder: Decoder[Type] = implicitly[Decoder[A]].map(wrap)

    implicit final class UnwrapOps(value: Type) {
      def unwrap: A = self.unwrap(value)
    }

    def makeUnsafe(value: A): Type =
      make(value).fold(e => throw new IllegalArgumentException(e.mkString("; ")), identity)
  }

  object RichNewtype {

    def wrap[FROM, TO](a: FROM)(implicit equiv: Equivalence[FROM, TO]): TO =
      implicitly[Equivalence[FROM, TO]].to(a)

    def unwrap[FROM, TO](a: TO)(implicit equiv: Equivalence[FROM, TO]): FROM =
      implicitly[Equivalence[FROM, TO]].from(a)
  }

  abstract class TaggedId extends RichNewtype[Long] {
    def empty: Option[Type] = None
  }

  object PodcastId extends TaggedId
  type PodcastId = PodcastId.Type

  object EpisodeId extends TaggedId
  type EpisodeId = EpisodeId.Type

  object ConfigurationId extends TaggedId
  type ConfigurationId = ConfigurationId.Type

  object YouTubeApiKey extends RichNewtype[String] {
    val configKey: String = "PODPODGE_YOUTUBE_API_KEY"
  }
  type YouTubeApiKey = YouTubeApiKey.Type

  object ServerHost extends RichNewtype[String] {
    val configKey: String = "PODPODGE_HOST"
  }
  type ServerHost = ServerHost.Type

  /** The network interface the server listens on, as opposed to [[ServerHost]],
    * which is the address Podpodge advertises to podcast apps in generated RSS
    * feeds. These are usually the same, but must differ when the address
    * clients reach Podpodge on isn't an address Podpodge can bind to -- most
    * notably inside a container, which has to listen on 0.0.0.0 while still
    * advertising a hostname/IP that's reachable from outside it.
    *
    * Env-only (not in the `configuration` table) because it describes the
    * machine Podpodge runs on rather than the deployment's public identity, so
    * it doesn't travel with the database.
    */
  object ServerBindHost extends RichNewtype[String] {
    val configKey: String = "PODPODGE_BIND_HOST"
  }
  type ServerBindHost = ServerBindHost.Type

  object ServerPort extends RichNewtype[Int] {
    val configKey: String = "PODPODGE_PORT"

    override def assertion = assert(greaterThanOrEqualTo(0) && lessThanOrEqualTo(65535))
  }
  type ServerPort = ServerPort.Type

  object ServerScheme extends RichNewtype[String] {
    val configKey: String = "PODPODGE_SCHEME"
  }
  type ServerScheme = ServerScheme.Type

  object DownloaderPath extends RichNewtype[String] {
    val configKey: String = "PODPODGE_DOWNLOADER_PATH"
  }
  type DownloaderPath = DownloaderPath.Type

  object AutoCheckAllPodcastUpdates extends RichNewtype[Boolean] {
    val configKey: String = "PODPODGE_AUTO_CHECK_ALL_PODCAST_UPDATES"
  }
  type AutoCheckAllPodcastUpdates = AutoCheckAllPodcastUpdates.Type

}
