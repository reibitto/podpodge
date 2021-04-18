package podpodge

import io.circe.{ Decoder, Encoder }
import zio.prelude._
import zio.test.Assertion

package object types {

  abstract class RichNewtype[A: Encoder: Decoder] extends Newtype[A] { self =>
    implicit val equiv: A <=> Type = Equivalence(wrap, unwrap)

    implicit val encoder: Encoder[Type] = implicitly[Encoder[A]].contramap(unwrap)
    implicit val decoder: Decoder[Type] = implicitly[Decoder[A]].map(wrap)

    implicit final class UnwrapOps(value: Type) {
      def unwrap: A = self.unwrap(value)
    }
  }

  object RichNewtype {
    def wrap[FROM, TO <: RichNewtype[FROM]#Type](a: FROM)(implicit equiv: Equivalence[FROM, TO]): TO =
      implicitly[Equivalence[FROM, TO]].to(a)

    def unwrap[FROM, TO, _ <: RichNewtype[FROM]#Type](a: TO)(implicit equiv: Equivalence[FROM, TO]): FROM =
      implicitly[Equivalence[FROM, TO]].from(a)
  }

  abstract class RichNewtypeSmart[A: Encoder: Decoder](spec: Assertion[A]) extends NewtypeSmart[A](spec) { self =>
    implicit val equiv: A <=> Type = Equivalence(wrap, unwrap)

    implicit val encoder: Encoder[Type] = implicitly[Encoder[A]].contramap(unwrap)
    implicit val decoder: Decoder[Type] = implicitly[Decoder[A]].map(wrap)

    implicit final class UnwrapOps(value: Type) {
      def unwrap: A = self.unwrap(value)
    }

    def makeUnsafe(value: A): Type = make(value).runEither.fold(e => throw new IllegalArgumentException(e), identity)
  }

  object RichNewtypeSmart {
    def wrap[FROM, TO <: RichNewtypeSmart[FROM]#Type](a: FROM)(implicit equiv: Equivalence[FROM, TO]): TO =
      implicitly[Equivalence[FROM, TO]].to(a)

    def unwrap[FROM, TO, _ <: RichNewtypeSmart[FROM]#Type](a: TO)(implicit equiv: Equivalence[FROM, TO]): FROM =
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

  object ServerPort extends RichNewtypeSmart[Int](isGreaterThanEqualTo(0) && isLessThanEqualTo(65353)) {
    val configKey: String = "PODPODGE_PORT"
  }
  type ServerPort = ServerPort.Type

  object ServerScheme extends RichNewtype[String] {
    val configKey: String = "PODPODGE_SCHEME"
  }
  type ServerScheme = ServerScheme.Type

}
