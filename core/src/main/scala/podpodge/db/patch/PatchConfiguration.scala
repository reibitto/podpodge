package podpodge.db.patch

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import podpodge.types.*

final case class PatchConfiguration(
    youTubeApiKey: Tristate[YouTubeApiKey] = Tristate.None,
    serverHost: Tristate[ServerHost] = Tristate.None,
    serverPort: Tristate[ServerPort] = Tristate.None,
    serverScheme: Tristate[ServerScheme] = Tristate.None,
    downloaderPath: Tristate[DownloaderPath] = Tristate.None
)

object PatchConfiguration {
  implicit val encoder: Encoder[PatchConfiguration] = deriveEncoder[PatchConfiguration]

  implicit val decoder: Decoder[PatchConfiguration] =
    Decoder.instance { c =>
      for {
        youTubeApiKey  <- c.get[Tristate[YouTubeApiKey]]("youTubeApiKey")
        serverHost     <- c.get[Tristate[ServerHost]]("serverHost")
        serverPort     <- c.get[Tristate[ServerPort]]("serverPort")
        serverScheme   <- c.get[Tristate[ServerScheme]]("serverScheme")
        downloaderPath <- c.get[Tristate[DownloaderPath]]("downloaderPath")
      } yield PatchConfiguration(
        youTubeApiKey,
        serverHost,
        serverPort,
        serverScheme,
        downloaderPath
      )
    }
}
