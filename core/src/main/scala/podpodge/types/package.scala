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

    def localhost: ServerHost = makeUnsafe("127.0.0.1")
  }
  type ServerHost = ServerHost.Type

  object ServerPort extends RichNewtype[Int] {
    val configKey: String = "PODPODGE_PORT"

    override def assertion = assert(greaterThanOrEqualTo(0) && lessThanOrEqualTo(65353))
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

}
