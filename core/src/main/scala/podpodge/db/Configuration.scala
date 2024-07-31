package podpodge.db

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import podpodge.types
import podpodge.types.*

// All fields are optional because there are other config sources to fall back to (like env vars).
final case class Configuration[ID](
    id: ID,
    youTubeApiKey: Option[YouTubeApiKey],
    serverHost: Option[ServerHost],
    serverPort: Option[ServerPort],
    serverScheme: Option[ServerScheme],
    downloaderPath: Option[DownloaderPath],
    openBrowser: Option[OpenBrowser],
    autoCheckAllPodcastUpdates: Option[AutoCheckAllPodcastUpdates]
)

object Configuration {
  type Model = Configuration[ConfigurationId]
  type Insert = Configuration[Option[ConfigurationId]]

  implicit val encoder: Encoder[Configuration.Model] = deriveEncoder[Configuration.Model]
  implicit val decoder: Decoder[Configuration.Model] = deriveDecoder[Configuration.Model]

  def empty: Configuration[Option[ConfigurationId.Type]] =
    Configuration(
      id = ConfigurationId.empty,
      youTubeApiKey = None,
      serverHost = None,
      serverPort = None,
      serverScheme = None,
      downloaderPath = None,
      openBrowser = None,
      autoCheckAllPodcastUpdates = None
    )
}
