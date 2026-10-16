package podpodge.db.patch

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import podpodge.types.{
  AutoCheckAllPodcastUpdates,
  DownloaderPath,
  ServerHost,
  ServerPort,
  ServerScheme,
  Tristate,
  YouTubeApiKey
}

final case class PatchConfiguration(
    youTubeApiKey: Tristate[YouTubeApiKey] = Tristate.None,
    serverHost: Tristate[ServerHost] = Tristate.None,
    serverPort: Tristate[ServerPort] = Tristate.None,
    serverScheme: Tristate[ServerScheme] = Tristate.None,
    downloaderPath: Tristate[DownloaderPath] = Tristate.None,
    autoCheckAllPodcastUpdates: Tristate[AutoCheckAllPodcastUpdates] = Tristate.None
)

object PatchConfiguration {
  implicit val encoder: Encoder[PatchConfiguration] = deriveEncoder[PatchConfiguration]

  // Handwritten because the derived `decodeAccumulating` (used by tapir) rejects missing fields before calling the
  // field decoder, which breaks `Tristate.Unspecified`.
  implicit val decoder: Decoder[PatchConfiguration] = Decoder.instance { c =>
    for {
      youTubeApiKey  <- c.downField("youTubeApiKey").as[Tristate[YouTubeApiKey]]
      serverHost     <- c.downField("serverHost").as[Tristate[ServerHost]]
      serverPort     <- c.downField("serverPort").as[Tristate[ServerPort]]
      serverScheme   <- c.downField("serverScheme").as[Tristate[ServerScheme]]
      downloaderPath <- c.downField("downloaderPath").as[Tristate[DownloaderPath]]
      autoCheckAllPodcastUpdates <-
        c.downField("autoCheckAllPodcastUpdates").as[Tristate[AutoCheckAllPodcastUpdates]]
    } yield PatchConfiguration(
      youTubeApiKey,
      serverHost,
      serverPort,
      serverScheme,
      downloaderPath,
      autoCheckAllPodcastUpdates
    )
  }
}
