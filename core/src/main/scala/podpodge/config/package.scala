package podpodge

import podpodge.db.dao.ConfigurationDao
import podpodge.types._
import sttp.model.Uri
import zio._
import zio.blocking.Blocking
import zio.config.ReadError
import zio.system.System

import java.sql.Connection

package object config {
  type Config = Has[PodpodgeConfig]

  object Config {
    def live: ZLayer[Has[Connection] with Blocking with System, ReadError[String], Has[PodpodgeConfig]] = {
      import zio.config.ConfigDescriptor._
      import zio.config._

      def stringNew(ns: RichNewtype[String])(path: String): ConfigDescriptor[ns.Type] =
        string(path).transform(ns.wrap(_), ns.unwrap)

      def intSmart(ns: RichNewtypeSmart[Int])(path: String): ConfigDescriptor[ns.Type] =
        int(path).transformOrFailLeft(n => ns.make(n).toEitherWith(_.mkString("; ")))(ns.unwrap)

      val configDesc: ConfigDescriptor[PodpodgeConfig] =
        (stringNew(YouTubeApiKey)(YouTubeApiKey.configKey).optional |@|
          stringNew(ServerHost)(ServerHost.configKey) |@|
          intSmart(ServerPort)(ServerPort.configKey) |@|
          stringNew(ServerScheme)(ServerScheme.configKey))(PodpodgeConfig.apply, PodpodgeConfig.unapply)

      (for {
        envSource      <- ConfigSource.fromSystemEnv
        defaultSource   = ConfigSource.fromMap(
                            Map(
                              ServerHost.configKey   -> "localhost",
                              ServerPort.configKey   -> "8080",
                              ServerScheme.configKey -> "http"
                            )
                          )
        fromDb         <-
          ConfigurationDao.getPrimary.bimap(
            t => ReadError.SourceError(s"Error reading config from database: ${t.getMessage}"),
            c =>
              (
                c.youTubeApiKey.map(s => YouTubeApiKey.configKey -> s.unwrap) ++
                  c.serverHost.map(s => ServerHost.configKey -> s.unwrap) ++
                  c.serverPort.map(s => ServerPort.configKey -> s.unwrap.toString) ++
                  c.serverScheme.map(s => ServerScheme.configKey -> s.unwrap)
              ).toMap
          )
        allSources      = envSource.orElse(ConfigSource.fromMap(fromDb)).orElse(defaultSource)
        podpodgeConfig <- ZIO.fromEither(zio.config.read(configDesc.from(allSources)))
      } yield podpodgeConfig).toLayer
    }
  }

  case class PodpodgeConfig(
    youTubeApiKey: Option[YouTubeApiKey],
    serverHost: ServerHost,
    serverPort: ServerPort,
    serverScheme: ServerScheme
  ) {
    val baseUri: Uri = Uri(serverScheme.unwrap, serverHost.unwrap, serverPort.unwrap)
  }

  def get: RIO[Config, PodpodgeConfig] = ZIO.service[PodpodgeConfig]

  def youTubeApiKey: RIO[Config, YouTubeApiKey] =
    ZIO
      .access[Config](_.get.youTubeApiKey)
      .someOrFail(
        new Exception(
          s"${YouTubeApiKey.configKey} is empty but it's required for this operation. You can set it as an environment variable or in the `configuration` table."
        )
      )

}
