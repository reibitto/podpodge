package podpodge.youtube

import io.circe.generic.semiauto.*
import io.circe.Decoder

import java.time.Duration

final case class VideoListResponse(items: List[Video])

object VideoListResponse {
  implicit val decoder: Decoder[VideoListResponse] = deriveDecoder[VideoListResponse]
}

final case class Video(id: String, contentDetails: VideoContentDetails)

object Video {
  implicit val decoder: Decoder[Video] = deriveDecoder[Video]
}

final case class VideoContentDetails(duration: Duration)

object VideoContentDetails {
  // YouTube reports duration in ISO-8601 format (e.g. "PT4M13S"), which `java.time.Duration.parse` understands
  // directly.
  implicit val durationDecoder: Decoder[Duration] = Decoder.decodeString.emapTry(s => scala.util.Try(Duration.parse(s)))

  implicit val decoder: Decoder[VideoContentDetails] = deriveDecoder[VideoContentDetails]
}
