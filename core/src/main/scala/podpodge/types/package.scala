package podpodge

import zio.prelude._

package object types {

  trait RichNewtype[A] extends Newtype[A] { self =>
    implicit val equiv: A <=> Type = Equivalence(wrap, unwrap)

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

  trait TaggedId extends RichNewtype[Long] {
    def empty: Option[Type] = None
  }

  object PodcastId extends TaggedId
  type PodcastId = PodcastId.Type

  object EpisodeId extends TaggedId
  type EpisodeId = EpisodeId.Type

}
