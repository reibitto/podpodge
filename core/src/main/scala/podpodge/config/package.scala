package podpodge

import podpodge.db.dao.ConfigurationDao
import podpodge.types._
import sttp.model.Uri
import zio._
import zio.prelude.Newtype

package object config {
  type Config = PodpodgeConfig

  object Config {
    def live =
      ZLayer {
        // TODO: This code is repetitious and somewhat brittle (newtypes are adding some safety though). We really
        // should use a config library here. Something like ciris, pureconfig, zio-config, etc. I haven't decided on one
        // yet.
        for {
          dbConfig       <- ConfigurationDao.getPrimary.orDie
          youTubeApiKey  <-
            ZIO
              .fromOption(dbConfig.youTubeApiKey)
              .orElse(
                System.env(YouTubeApiKey.configKey).some.flatMap(s => ZIO.fromOption(YouTubeApiKey.make(s).toOption))
              )
              .option
          serverHost     <-
            ZIO
              .fromOption(dbConfig.serverHost)
              .orElse(
                System.env(ServerHost.configKey).some.flatMap(s => ZIO.fromOption(ServerHost.make(s).toOption))
              )
              .orElseSucceed(ServerHost("localhost"))
          serverPort     <-
            ZIO
              .fromOption(dbConfig.serverPort)
              .orElse(
                System
                  .env(ServerPort.configKey)
                  .some
                  .flatMap(s => ZIO.fromOption(s.toIntOption.flatMap(n => ServerPort.make(n).toOption)))
              )
              .orElseSucceed(ServerPort(8080))
          serverScheme   <-
            ZIO
              .fromOption(dbConfig.serverScheme)
              .orElse(
                System.env(ServerScheme.configKey).some.flatMap(s => ZIO.fromOption(ServerScheme.make(s).toOption))
              )
              .orElseSucceed(ServerScheme("http"))
          downloaderPath <-
            ZIO
              .fromOption(dbConfig.downloaderPath)
              .orElse(
                System.env(DownloaderPath.configKey).some.flatMap(s => ZIO.fromOption(DownloaderPath.make(s).toOption))
              )
              .orElseSucceed(DownloaderPath("youtube-dl"))
        } yield PodpodgeConfig(
          youTubeApiKey,
          serverHost,
          serverPort,
          serverScheme,
          downloaderPath
        )
      }
  }

  case class PodpodgeConfig(
    youTubeApiKey: Option[YouTubeApiKey],
    serverHost: ServerHost,
    serverPort: ServerPort,
    serverScheme: ServerScheme,
    downloaderPath: DownloaderPath
  ) {
    val baseUri: Uri = Uri(serverScheme.unwrap, serverHost.unwrap, serverPort.unwrap)
  }

  def get: URIO[Config, PodpodgeConfig] = ZIO.service[PodpodgeConfig]

  def youTubeApiKey: RIO[Config, YouTubeApiKey] =
    ZIO
      .serviceWith[Config](_.youTubeApiKey)
      .someOrFail(
        new Exception(
          s"${YouTubeApiKey.configKey} is empty but it's required for this operation. You can set it as an environment variable or in the `configuration` table."
        )
      )

}
