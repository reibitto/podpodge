package podpodge.db

import java.io.File
import java.nio.file.Path
import java.time.{ Duration, OffsetDateTime }

import podpodge.Config
import podpodge.types.{ EpisodeId, PodcastId, _ }
import sttp.client._
import sttp.model.Uri

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
    image.flatMap(name => Try(Config.thumbnailsPath.resolve(podcastId.unwrap.toString).resolve(name)).toOption)

  def linkUrl(sourceType: SourceType): Uri = sourceType match {
    case SourceType.YouTube   => uri"https://www.youtube.com/playlist?list=$externalSource"
    case SourceType.Directory =>
      val file = new File(externalSource)
      Uri.unsafeParse(s"file://${file.getCanonicalPath}")
  }
}

object Episode {
  type Model  = Episode[EpisodeId]
  type Insert = Episode[Option[EpisodeId]]
}
