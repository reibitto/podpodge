package podpodge.youtube

import io.circe.generic.semiauto.*
import io.circe.Decoder

import java.time.OffsetDateTime

final case class PlaylistItemListResponse(
    items: List[PlaylistItem],
    nextPageToken: Option[String],
    pageInfo: PageInfo
)

object PlaylistItemListResponse {
  implicit val decoder: Decoder[PlaylistItemListResponse] = deriveDecoder[PlaylistItemListResponse]
}

final case class PlaylistItem(id: String, snippet: PlaylistItemSnippet, contentDetails: PlaylistItemContentDetails) {
  def isPrivate: Boolean = contentDetails.videoPublishedAt.isEmpty
}

object PlaylistItem {
  implicit val decoder: Decoder[PlaylistItem] = deriveDecoder[PlaylistItem]
}

final case class PlaylistItemSnippet(
    publishedAt: OffsetDateTime,
    channelId: String,
    title: String,
    description: String,
    thumbnails: Thumbnails,
    channelTitle: String,
    playlistId: String,
    resourceId: ResourceId
)

object PlaylistItemSnippet {
  implicit val decoder: Decoder[PlaylistItemSnippet] = deriveDecoder[PlaylistItemSnippet]
}

final case class Thumbnails(
    default: Option[Thumbnail],
    medium: Option[Thumbnail],
    high: Option[Thumbnail],
    standard: Option[Thumbnail],
    maxres: Option[Thumbnail]
) {
  def highestRes: Option[Thumbnail] = maxres.orElse(high).orElse(standard).orElse(medium).orElse(default)
}

object Thumbnails {
  implicit val decoder: Decoder[Thumbnails] = deriveDecoder[Thumbnails]
}

final case class Thumbnail(url: String, width: Int, height: Int)

object Thumbnail {
  implicit val decoder: Decoder[Thumbnail] = deriveDecoder[Thumbnail]
}

final case class ResourceId(kind: String, videoId: String)

object ResourceId {
  implicit val decoder: Decoder[ResourceId] = deriveDecoder[ResourceId]
}

final case class PlaylistItemContentDetails(videoId: String, videoPublishedAt: Option[OffsetDateTime])

object PlaylistItemContentDetails {
  implicit val decoder: Decoder[PlaylistItemContentDetails] = deriveDecoder[PlaylistItemContentDetails]
}

final case class PageInfo(totalResults: Int, resultsPerPage: Int)

object PageInfo {
  implicit val decoder: Decoder[PageInfo] = deriveDecoder[PageInfo]
}
