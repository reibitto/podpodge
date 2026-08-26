package podpodge.db.patch

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import podpodge.types.{DownloaderPath, ServerHost, ServerPort, ServerScheme, Tristate, YouTubeApiKey}

final case class PatchConfiguration(
    youTubeApiKey: Tristate[YouTubeApiKey] = Tristate.None,
    serverHost: Tristate[ServerHost] = Tristate.None,
    serverPort: Tristate[ServerPort] = Tristate.None,
    serverScheme: Tristate[ServerScheme] = Tristate.None,
    downloaderPath: Tristate[DownloaderPath] = Tristate.None
)

object PatchConfiguration {
  implicit val encoder: Encoder[PatchConfiguration] = deriveEncoder[PatchConfiguration]

  // Handwritten rather than derived: circe-generic's macro-derived `decodeAccumulating` (which tapir's jsonBody
  // uses under the hood) has its own "field missing => error" check that runs before ever consulting the field's
  // own Decoder.
  implicit val decoder: Decoder[PatchConfiguration] = Decoder.instance { c =>
    for {
      youTubeApiKey  <- c.downField("youTubeApiKey").as[Tristate[YouTubeApiKey]]
      serverHost     <- c.downField("serverHost").as[Tristate[ServerHost]]
      serverPort     <- c.downField("serverPort").as[Tristate[ServerPort]]
      serverScheme   <- c.downField("serverScheme").as[Tristate[ServerScheme]]
      downloaderPath <- c.downField("downloaderPath").as[Tristate[DownloaderPath]]
    } yield PatchConfiguration(youTubeApiKey, serverHost, serverPort, serverScheme, downloaderPath)
  }
}
