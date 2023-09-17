package podpodge.youtube

import io.circe.generic.semiauto.*
import io.circe.Decoder

import java.time.OffsetDateTime

final case class PlaylistListResponse(
    items: List[Playlist],
    nextPageToken: Option[String],
    pageInfo: PageInfo
)

object PlaylistListResponse {
  implicit val decoder: Decoder[PlaylistListResponse] = deriveDecoder[PlaylistListResponse]
}

final case class Playlist(
    id: String,
    snippet: PlaylistSnippet,
    contentDetails: PlaylistContentDetails
)

object Playlist {
  implicit val decoder: Decoder[Playlist] = deriveDecoder[Playlist]
}

final case class PlaylistSnippet(
    publishedAt: OffsetDateTime,
    channelId: String,
    title: String,
    description: String,
    thumbnails: Thumbnails,
    channelTitle: String
)

object PlaylistSnippet {
  implicit val decoder: Decoder[PlaylistSnippet] = deriveDecoder[PlaylistSnippet]
}

final case class PlaylistContentDetails(itemCount: Int)

object PlaylistContentDetails {
  implicit val decoder: Decoder[PlaylistContentDetails] = deriveDecoder[PlaylistContentDetails]
}
