package podpodge

import podpodge.db.dao.ConfigurationDao
import podpodge.types._
import sttp.model.Uri
import zio._
import zio.config._

import java.sql.Connection
import zio.System

import javax.sql.DataSource

package object config {
  type Config = PodpodgeConfig

  object Config {
    def live: ZLayer[DataSource, ReadError[String], PodpodgeConfig] = {
      ZLayer {
        for {
          _ <- ZIO.unit
        } yield PodpodgeConfig(
          Some(YouTubeApiKey("")),
          ServerHost("localhost"),
          ServerPort(8080),
          ServerScheme("http"),
          //DownloaderPath("youtube-dl")
          DownloaderPath("yt-dlp")
        )
      }
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
