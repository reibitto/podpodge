package podpodge.db

import java.time.{ Duration, OffsetDateTime }

import podpodge.types.{ EpisodeId, PodcastId }

final case class Episode[ID](
  id: ID,
  podcastId: PodcastId.Type,
  guid: String,
  externalSource: String,
  title: String,
  publishDate: OffsetDateTime,
  image: Option[String],
  mediaFile: Option[String],
  duration: Duration
)

object Episode {
  type Model  = Episode[EpisodeId.Type]
  type Insert = Episode[Option[EpisodeId.Type]]
}
