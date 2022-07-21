package podpodge

import zio.ZLayer
import zio.logging.LogFormat._
import zio.logging.{LogColor, LogFormat, console}

object PodpodgeLogging {

  private val coloredFormat: LogFormat =
    timestamp.color(LogColor.BLUE) |-|
      level.highlight |-|
      fiberId.color(LogColor.WHITE) |-|
      line.highlight

  val default: ZLayer[Any, Nothing, Unit] = console(coloredFormat)

}
