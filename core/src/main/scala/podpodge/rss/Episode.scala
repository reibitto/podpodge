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
    imageUrl: Uri,
    // Enclosure size in bytes. 0 if not downloaded yet, since YouTube episodes are fetched on demand.
    mediaLength: Long,
    mediaType: String
)
