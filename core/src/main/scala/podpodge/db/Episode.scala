package podpodge.db

import java.nio.file.Path
import java.time.{ Duration, OffsetDateTime }

import podpodge.Config
import podpodge.types.{ EpisodeId, PodcastId }
import podpodge.types._

import scala.util.Try

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
) {
  def imagePath: Option[Path] =
    image.flatMap(name => Try(Config.thumbnailsPath.resolve(podcastId.unwrap.toString).resolve(name)).toOption)
}

object Episode {
  type Model  = Episode[EpisodeId.Type]
  type Insert = Episode[Option[EpisodeId.Type]]
}
