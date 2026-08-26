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
    // The enclosure's byte size, per the RSS spec. 0 when the file hasn't been downloaded yet (YouTube episodes are
    // fetched on demand), since the real size can't be known in advance.
    mediaLength: Long
)
