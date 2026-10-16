package podpodge.rss

import podpodge.config.PodpodgeConfig
import podpodge.db
import podpodge.types.EpisodeId
import podpodge.util.FileExtensions.*
import sttp.model.Uri

import java.time.OffsetDateTime

final case class Podcast(
    title: String,
    linkUrl: Uri,
    selfUrl: Uri,
    description: String,
    category: String,
    generator: String,
    lastBuildDate: OffsetDateTime,
    publishDate: OffsetDateTime,
    author: String,
    subtitle: String,
    summary: String,
    imageUrl: Uri,
    items: List[Episode]
)

object Podcast {

  def fromDB(
      podcast: db.Podcast.Model,
      episodes: List[db.Episode.Model],
      mediaLengths: Map[EpisodeId, Long],
      config: PodpodgeConfig
  ): Podcast =
    Podcast(
      podcast.title,
      podcast.linkUrl,
      config.baseUri.withPath("podcast", podcast.id.unwrap.toString, "rss"),
      podcast.description,
      podcast.category,
      podcast.generator,
      podcast.lastBuildDate,
      podcast.publishDate,
      podcast.author,
      podcast.subtitle,
      podcast.summary,
      config.baseUri.withPath("cover", podcast.id.unwrap.toString),
      episodes.map { episode =>
        Episode(
          config.baseUri.withPath("episode", episode.id.unwrap.toString, "file"),
          episode.externalSource,
          episode.linkUrl(podcast.sourceType),
          episode.title,
          episode.publishDate,
          episode.duration,
          config.baseUri.withPath("thumbnail", episode.id.unwrap.toString),
          mediaLengths.getOrElse(episode.id, 0L),
          s"audio/${episode.mediaFilePath(podcast.sourceType).toFile.audioMediaSubtype}"
        )
      }
    )
}
