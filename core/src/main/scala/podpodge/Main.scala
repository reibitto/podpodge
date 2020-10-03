package podpodge

import podpodge.server.PodpodgeServer
import zio._
import zio.logging.log

object Main extends zio.App {
  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    (for {
      fiber    <- PodpodgeServer.make.useForever.fork
      _        <- log.info(s"Starting server at ${Config.baseUri}")
      exitCode <- fiber.join.exitCode
    } yield exitCode).provide(PodpodgeRuntime.environment)
}
