package podpodge

import podpodge.db.DbMigration
import podpodge.server.PodpodgeServer
import zio._

object Main extends ZIOAppDefault {
  def run =
    (for {
      _        <- DbMigration.migrate.orDie
      _ <- ZIO.scoped {
        PodpodgeServer.make *> ZIO.never
      }.forever
      //.provideEnvironment(PodpodgeRuntime.default.environment)
    } yield ()).provideEnvironment(PodpodgeRuntime.default.environment)
}
