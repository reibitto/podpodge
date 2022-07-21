package podpodge

import io.getquill.context.ZioJdbc.DataSourceLayer
import podpodge.config.Config
import podpodge.db.DbMigration
import podpodge.http.SttpLive
import podpodge.server.PodpodgeServer
import zio.*

object Main extends ZIOApp {
  override type Environment = Env

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  override def bootstrap: ZLayer[Scope, Any, Environment] = ZLayer.make[Environment](
    SttpLive.make,
    DataSourceLayer.fromPrefix("ctx"),
    Config.live,
    Runtime.removeDefaultLoggers >>> PodpodgeLogging.default
  )

  def run: ZIO[Env with ZIOAppArgs with Scope, Throwable, Unit] =
    for {
      _ <- DbMigration.migrate.orDie
      _ <- ZIO.scoped {
             PodpodgeServer.make *> ZIO.never
           }
    } yield ()
}
