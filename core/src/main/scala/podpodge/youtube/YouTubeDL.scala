package podpodge.youtube

import podpodge.types.*
import podpodge.StaticConfig
import zio.{Task, ZIO}
import zio.process.Command

import java.io.File
import java.nio.file.Files

object YouTubeDL {

  def download(
    podcastId: PodcastId,
    videoId: String,
    downloaderPathOpt: Option[DownloaderPath]
  ): Task[File] = {
    // TODO: Support other audio formats in the future. Note that `EpisodeController` and so on
    // will have to be updated as well since "mp3" is hardcoded there.
    val audioFormat = "mp3"
    val podcastAudioDirectory = StaticConfig.audioPath.resolve(podcastId.unwrap.toString)
    val outputFile = podcastAudioDirectory.resolve(s"$videoId.$audioFormat").toFile
    val downloaderPath = downloaderPathOpt.getOrElse(DownloaderPath("youtube-dl"))

    if (outputFile.exists) {
      ZIO.logInfo(s"${outputFile.getName} already exists. Skipping download.").as(outputFile)
    } else {
      for {
        workingDirectory <- ZIO.attempt(Files.createDirectories(podcastAudioDirectory))
        // Pass a URL to youtube-dl instead of just the videoId because YouTube's IDs can start with a hyphen which
        // confuses youtube-dl into thinking it's a command-line option.
        videoUrl = s"https://www.youtube.com/watch?v=$videoId"
        // VBR can cause slowness with seeks in podcast apps, so we use a constant bitrate instead.
        _ <- Command(
               downloaderPath.unwrap,
               "--no-call-home",
               "--extract-audio",
               "--audio-format",
               audioFormat,
               "--audio-quality",
               "128K",
               "--output",
               outputFile.getName,
               videoUrl
             ).workingDirectory(workingDirectory.toFile).inheritIO.successfulExitCode
      } yield outputFile
    }
  }
}
