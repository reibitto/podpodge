package podpodge.db

import podpodge.types.{EpisodeId, PodcastId, *}
import podpodge.StaticConfig
import sttp.client3.*
import sttp.model.Uri

import java.io.File
import java.nio.file.{Path, Paths}
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
    case SourceType.YouTube   => uri"https://www.youtube.com/watch?v=$externalSource"
    case SourceType.Directory => Uri(new File(externalSource).toURI)
  }

  // Where the downloaded audio file lives on disk, matching the convention used by `YouTubeDL.download` and
  // `EpisodeController`.
  def mediaFilePath(sourceType: SourceType): Path = sourceType match {
    case SourceType.YouTube   => StaticConfig.audioPath.resolve(podcastId.unwrap.toString).resolve(s"$externalSource.mp3")
    case SourceType.Directory => Paths.get(externalSource)
  }
}

object Episode {
  type Model = Episode[EpisodeId]
  type Insert = Episode[Option[EpisodeId]]
}
