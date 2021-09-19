package podpodge.db

import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }
import podpodge.types._

// All fields are optional because there are other config sources to fall back to (like env vars).
final case class Configuration[ID](
  id: ID,
  youTubeApiKey: Option[YouTubeApiKey],
  serverHost: Option[ServerHost],
  serverPort: Option[ServerPort],
  serverScheme: Option[ServerScheme],
  downloaderPath: Option[DownloaderPath]
)

object Configuration {
  type Model  = Configuration[ConfigurationId]
  type Insert = Configuration[Option[ConfigurationId]]

  implicit val encoder: Encoder[Configuration.Model] = deriveEncoder[Configuration.Model]
  implicit val decoder: Decoder[Configuration.Model] = deriveDecoder[Configuration.Model]
}
