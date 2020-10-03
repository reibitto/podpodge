package podpodge.rss

import java.time.OffsetDateTime

import podpodge.types._
import podpodge.{ db, Config }
import sttp.client._
import sttp.model.Uri

final case class Podcast(
  title: String,
  linkUrl: Uri,
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
  def fromDB(podcast: db.Podcast.Model, episodes: List[db.Episode.Model]): Podcast =
    Podcast(
      podcast.title,
      uri"https://www.youtube.com/playlist?list=${podcast.externalSource}",
      podcast.description,
      podcast.category,
      podcast.generator,
      podcast.lastBuildDate,
      podcast.publishDate,
      podcast.author,
      podcast.subtitle,
      podcast.summary,
      Config.baseUri.path("cover", podcast.id.unwrap.toString),
      episodes.map { episode =>
        Episode(
          Config.baseUri.path("episode", episode.id.unwrap.toString, "file"),
          episode.externalSource,
          uri"https://www.youtube.com/watch?v=${episode.externalSource}",
          episode.title,
          episode.publishDate,
          episode.duration,
          Config.baseUri.path("thumbnail", episode.id.unwrap.toString)
        )
      }
    )
}
