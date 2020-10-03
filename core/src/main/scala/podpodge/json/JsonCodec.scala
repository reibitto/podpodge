package podpodge.json

import io.circe.{ Decoder, Encoder }
import podpodge.types.PodcastId
import podpodge.types._

object JsonCodec {
  implicit val podcastIdDecoder: Decoder[PodcastId.Type] = Decoder.decodeLong.map(PodcastId(_))
  implicit val podcastIdEncoder: Encoder[PodcastId.Type] = Encoder.encodeLong.contramap(_.unwrap)
}
