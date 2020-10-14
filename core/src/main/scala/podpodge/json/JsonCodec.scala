package podpodge.json

import io.circe.{ Decoder, Encoder }
import podpodge.types._

object JsonCodec {
  implicit val podcastIdDecoder: Decoder[PodcastId] = Decoder.decodeLong.map(PodcastId(_))
  implicit val podcastIdEncoder: Encoder[PodcastId] = Encoder.encodeLong.contramap(_.unwrap)
}
