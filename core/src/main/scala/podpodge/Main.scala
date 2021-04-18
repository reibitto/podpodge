package podpodge

import podpodge.db.DbMigration
import podpodge.server.PodpodgeServer
import zio._

object Main extends zio.App {
  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    for {
      _        <- DbMigration.migrate.orDie
      exitCode <- PodpodgeServer.make.useForever.exitCode.provide(PodpodgeRuntime.environment)
    } yield exitCode
}
