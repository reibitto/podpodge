package podpodge

import podpodge.db.dao.ConfigurationDao
import podpodge.types.*
import sttp.model.Uri
import zio.*

import javax.sql.DataSource

package object config {

  // Held in a Ref so PATCHing the configuration takes effect without a restart. The bind host and port are only read
  // at startup, so changing those still requires one.
  final case class Config(ref: Ref[PodpodgeConfig])

  object Config {

    def live: ZLayer[DataSource, Nothing, Config] =
      ZLayer(load.flatMap(Ref.make(_)).map(Config(_)))

    def load: URIO[DataSource, PodpodgeConfig] =
      // TODO: This code is repetitious and somewhat brittle (newtypes are adding some safety though). We really
      // should use a config library here. Something like ciris, pureconfig, zio-config, etc. I haven't decided on one
      // yet.
      for {
        dbConfig <- ConfigurationDao.getPrimary.orDie
        youTubeApiKey <-
          ZIO
            .fromOption(dbConfig.youTubeApiKey)
            .orElse(
              System.env(YouTubeApiKey.configKey).some.flatMap(s => ZIO.fromOption(YouTubeApiKey.make(s).toOption))
            )
            .option
        serverHost <-
          ZIO
            .fromOption(dbConfig.serverHost)
            .orElse(
              System.env(ServerHost.configKey).some.flatMap(s => ZIO.fromOption(ServerHost.make(s).toOption))
            )
            .orElseSucceed(ServerHost("localhost"))
        serverPort <-
          ZIO
            .fromOption(dbConfig.serverPort)
            .orElse(
              System
                .env(ServerPort.configKey)
                .some
                .flatMap(s => ZIO.fromOption(s.toIntOption.flatMap(n => ServerPort.make(n).toOption)))
            )
            .orElseSucceed(ServerPort(8080))
        serverScheme <-
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
            .orElseSucceed(DownloaderPath("yt-dlp"))
        bindHost <-
          System
            .env(ServerBindHost.configKey)
            .some
            .flatMap(s => ZIO.fromOption(ServerBindHost.make(s).toOption))
            .orElseSucceed(ServerBindHost(serverHost.unwrap))
        autoCheckAllPodcastUpdates <-
          ZIO
            .fromOption(dbConfig.autoCheckAllPodcastUpdates)
            .orElse(
              System
                .env(AutoCheckAllPodcastUpdates.configKey)
                .some
                .flatMap(s =>
                  ZIO.fromOption(s.toBooleanOption.flatMap(b => AutoCheckAllPodcastUpdates.make(b).toOption))
                )
            )
            .orElseSucceed(AutoCheckAllPodcastUpdates(true))
      } yield PodpodgeConfig(
        youTubeApiKey,
        serverHost,
        serverPort,
        serverScheme,
        downloaderPath,
        bindHost,
        autoCheckAllPodcastUpdates
      )
  }

  case class PodpodgeConfig(
      youTubeApiKey: Option[YouTubeApiKey],
      serverHost: ServerHost,
      serverPort: ServerPort,
      serverScheme: ServerScheme,
      downloaderPath: DownloaderPath,
      bindHost: ServerBindHost,
      autoCheckAllPodcastUpdates: AutoCheckAllPodcastUpdates
  ) {

    // Built from `serverHost`, not `bindHost`, since clients can't fetch from a wildcard address like 0.0.0.0.
    val baseUri: Uri = Uri(serverScheme.unwrap, serverHost.unwrap, serverPort.unwrap)
  }

  def get: URIO[Config, PodpodgeConfig] = ZIO.serviceWithZIO[Config](_.ref.get)

  def reload: URIO[Config & DataSource, Unit] =
    Config.load.flatMap(c => ZIO.serviceWithZIO[Config](_.ref.set(c)))

  def youTubeApiKey: RIO[Config, YouTubeApiKey] =
    get
      .map(_.youTubeApiKey)
      .someOrFail(
        new Exception(
          s"${YouTubeApiKey.configKey} is empty but it's required for this operation. You can set it as an environment variable or in the `configuration` table."
        )
      )

}
