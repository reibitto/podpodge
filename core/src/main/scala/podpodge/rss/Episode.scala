package podpodge.rss

import sttp.model.Uri

import java.time.{Duration, OffsetDateTime}

final case class Episode(
  downloadUrl: Uri,
  guid: String,
  linkUrl: Uri,
  title: String,
  publishDate: OffsetDateTime,
  duration: Duration,
  imageUrl: Uri
)
