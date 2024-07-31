package podpodge.db.patch

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import podpodge.types.*

final case class PatchConfiguration(
    youTubeApiKey: Tristate[YouTubeApiKey],
    serverHost: Tristate[ServerHost],
    serverPort: Tristate[ServerPort],
    serverScheme: Tristate[ServerScheme],
    downloaderPath: Tristate[DownloaderPath],
    openBrowser: Tristate[OpenBrowser],
    autoCheckAllPodcastUpdates: Tristate[AutoCheckAllPodcastUpdates]
)

object PatchConfiguration {
  implicit val encoder: Encoder[PatchConfiguration] = deriveEncoder[PatchConfiguration]

  implicit val decoder: Decoder[PatchConfiguration] =
    Decoder.instance { c =>
      for {
        youTubeApiKey              <- c.get[Tristate[YouTubeApiKey]]("youTubeApiKey")
        serverHost                 <- c.get[Tristate[ServerHost]]("serverHost")
        serverPort                 <- c.get[Tristate[ServerPort]]("serverPort")
        serverScheme               <- c.get[Tristate[ServerScheme]]("serverScheme")
        downloaderPath             <- c.get[Tristate[DownloaderPath]]("downloaderPath")
        openBrowser                <- c.get[Tristate[OpenBrowser]]("openBrowser")
        autoCheckAllPodcastUpdates <- c.get[Tristate[AutoCheckAllPodcastUpdates]]("autoCheckAllPodcastUpdates")
      } yield PatchConfiguration(
        youTubeApiKey,
        serverHost,
        serverPort,
        serverScheme,
        downloaderPath,
        openBrowser,
        autoCheckAllPodcastUpdates
      )
    }

  def empty: PatchConfiguration =
    PatchConfiguration(
      youTubeApiKey = Tristate.None,
      serverHost = Tristate.None,
      serverPort = Tristate.None,
      serverScheme = Tristate.None,
      downloaderPath = Tristate.None,
      openBrowser = Tristate.None,
      autoCheckAllPodcastUpdates = Tristate.None
    )
}
