package podpodge.youtube

import java.io.File
import java.nio.file.Files

import podpodge.Config
import podpodge.types.{ PodcastId, _ }
import zio.blocking.Blocking
import zio.logging.{ log, Logging }
import zio.process.Command
import zio.{ RIO, Task }

object YouTubeDL {
  def download(podcastId: PodcastId.Type, videoId: String): RIO[Blocking with Logging, File] = {
    val audioFormat = "mp3"

    val podcastAudioDirectory = Config.audioPath.resolve(podcastId.unwrap.toString)
    val outputFile            = podcastAudioDirectory.resolve(s"${videoId}.${audioFormat}").toFile

    if (outputFile.exists) {
      log.info(s"${outputFile.getName} already exists. Skipping download.").as(outputFile)
    } else {
      for {
        workingDirectory <- Task(Files.createDirectories(podcastAudioDirectory))
        _                <- Command(
                              "youtube-dl",
                              "--no-call-home",
                              "--extract-audio",
                              "--audio-format",
                              audioFormat,
                              "--output",
                              outputFile.getName,
                              videoId
                            ).workingDirectory(workingDirectory.toFile).inheritIO.successfulExitCode
      } yield outputFile
    }
  }
}
