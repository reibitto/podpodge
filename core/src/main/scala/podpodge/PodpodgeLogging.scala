package podpodge

import zio.logging.{console, LogColor, LogFormat}
import zio.logging.LogFormat.*
import zio.ZLayer

object PodpodgeLogging {

  private val coloredFormat: LogFormat =
    timestamp.color(LogColor.BLUE) |-|
      level.highlight |-|
      fiberId.color(LogColor.WHITE) |-|
      line.highlight

  val default: ZLayer[Any, Nothing, Unit] = console(coloredFormat)

}
