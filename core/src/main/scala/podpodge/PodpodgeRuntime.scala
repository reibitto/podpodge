package podpodge

import io.getquill.context.ZioJdbc.DataSourceLayer
import podpodge.config.Config
import podpodge.http.SttpLive
import zio.{ Runtime, Unsafe, ZLayer }

object PodpodgeRuntime {
  lazy val default: Runtime[Env] =
    Unsafe.unsafe { implicit u =>
      Runtime.unsafe.fromLayer(
        ZLayer.make[Env](
          SttpLive.make,
          DataSourceLayer.fromPrefix("ctx"),
          Config.live
        )
      )
    }
}
