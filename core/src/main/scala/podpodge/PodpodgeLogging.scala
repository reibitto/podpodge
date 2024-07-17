package podpodge

import zio.logging.consoleLogger
import zio.logging.ConsoleLoggerConfig
import zio.logging.LogColor
import zio.logging.LogFilter
import zio.logging.LogFilter.LogLevelByNameConfig
import zio.logging.LogFormat
import zio.logging.LogFormat.*
import zio.LogLevel
import zio.ZLayer

object PodpodgeLogging {

  val coloredFormat: LogFormat =
    timestamp.color(LogColor.BLUE) |-|
      level.highlight |-|
      fiberId.color(LogColor.WHITE) |-|
      line.highlight |-|
      newLine +
      cause.highlight.filter(LogFilter.causeNonEmpty)

  val default: ZLayer[Any, Nothing, Unit] =
    consoleLogger(ConsoleLoggerConfig(coloredFormat, LogLevelByNameConfig(LogLevel.Debug)))

}
