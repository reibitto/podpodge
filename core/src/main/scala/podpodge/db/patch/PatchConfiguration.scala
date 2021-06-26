package podpodge.db.patch

import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }
import podpodge.types.{ ServerHost, ServerPort, ServerScheme, Tristate, YouTubeApiKey }

final case class PatchConfiguration(
  youTubeApiKey: Tristate[YouTubeApiKey] = Tristate.None,
  serverHost: Tristate[ServerHost] = Tristate.None,
  serverPort: Tristate[ServerPort] = Tristate.None,
  serverScheme: Tristate[ServerScheme] = Tristate.None
)

object PatchConfiguration {
  implicit val encoder: Encoder[PatchConfiguration] = deriveEncoder[PatchConfiguration]
  implicit val decoder: Decoder[PatchConfiguration] = deriveDecoder[PatchConfiguration]
}
