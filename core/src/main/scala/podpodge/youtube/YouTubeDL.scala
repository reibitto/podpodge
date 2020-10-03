package podpodge.youtube

import java.io.File

import podpodge.Config
import zio.ZIO
import zio.blocking.Blocking
import zio.logging.{ log, Logging }
import zio.process.{ Command, CommandError }

object YouTubeDL {
  def download(videoId: String): ZIO[Blocking with Logging, CommandError, File] = {
    val audioFormat = "mp3"

    val outputFile = Config.audioPath.resolve(s"${videoId}.${audioFormat}").toFile

    if (outputFile.exists) {
      log.info(s"${outputFile.getName} already exists. Skipping download.").as(outputFile)
    } else {
      Command(
        "youtube-dl",
        "--no-call-home",
        "--extract-audio",
        "--audio-format",
        audioFormat,
        "--output",
        outputFile.getName,
        videoId
      ).workingDirectory(Config.audioPath.toFile).inheritIO.successfulExitCode.as(outputFile)
    }
  }
}
