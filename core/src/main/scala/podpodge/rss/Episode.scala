package podpodge.rss

import java.time.{ Duration, OffsetDateTime }

import sttp.model.Uri

final case class Episode(
  downloadUrl: Uri,
  guid: String,
  linkUrl: Uri,
  title: String,
  publishDate: OffsetDateTime,
  duration: Duration,
  imageUrl: Uri
)
