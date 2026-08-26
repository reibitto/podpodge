package podpodge.util

import zio.{Task, ZIO}
import zio.process.Command

import java.io.File
import java.time.Duration

object Ffprobe {

  // ffprobe ships alongside ffmpeg, which yt-dlp already depends on for audio extraction, so it should be available
  // wherever Podpodge's YouTube downloads work. Failures here are non-fatal: a missing/incorrect duration just means
  // podcast apps won't show one, which is preferable to failing episode creation entirely.
  def duration(file: File): Task[Option[Duration]] =
    Command("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file.getAbsolutePath).string
      .map(_.trim.toDoubleOption.map(seconds => Duration.ofMillis(Math.round(seconds * 1000))))
      .catchAll { t =>
        ZIO.logWarning(s"Failed to determine duration of '${file.getName}' via ffprobe: ${t.getMessage}").as(None)
      }
}
