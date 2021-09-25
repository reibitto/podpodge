package podpodge

import io.getquill.context.ZioJdbc.{ DataSourceLayer, QDataSource }
import podpodge.config.Config
import sttp.client.httpclient.zio.HttpClientZioBackend
import zio.internal.Platform
import zio.logging.{ LogLevel, Logging }
import zio.{ Runtime, ZEnv, ZLayer }

trait PodpodgeRuntime extends Runtime[Env] {
  lazy val default: Runtime.Managed[Env] = Runtime.unsafeFromLayer {
    import zio.magic._

    ZLayer.fromMagic[Env](
      ZEnv.live,
      Logging.console(LogLevel.Trace),
      HttpClientZioBackend.layer(),
      QDataSource.fromPrefix("ctx"),
      DataSourceLayer.live,
      Config.live
    )
  }

  lazy val environment: Env              = default.environment
  lazy val platform: Platform            = default.platform
}

object PodpodgeRuntime extends PodpodgeRuntime
