package podpodge.db

import podpodge.types.{EpisodeId, PodcastId, *}
import podpodge.StaticConfig
import sttp.client3.*
import sttp.model.Uri

import java.io.File
import java.nio.file.Path
import java.time.{Duration, OffsetDateTime}
import scala.util.Try

final case class Episode[ID](
    id: ID,
    podcastId: PodcastId,
    guid: String,
    externalSource: String,
    title: String,
    publishDate: OffsetDateTime,
    image: Option[String],
    mediaFile: Option[String],
    duration: Duration
) {

  def imagePath: Option[Path] =
    image.flatMap(name => Try(StaticConfig.thumbnailsPath.resolve(podcastId.unwrap.toString).resolve(name)).toOption)

  def linkUrl(sourceType: SourceType): Uri = sourceType match {
    case SourceType.YouTube   => uri"https://www.youtube.com/playlist?list=$externalSource"
    case SourceType.Directory => Uri(new File(externalSource).toURI)
  }
}

object Episode {
  type Model = Episode[EpisodeId]
  type Insert = Episode[Option[EpisodeId]]
}
