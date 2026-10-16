package podpodge.youtube

import podpodge.types.*
import podpodge.StaticConfig
import zio.*
import zio.process.Command

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeoutException

object YouTubeDL {

  // yt-dlp can hang (stalled connection, locked cookie database). Without a timeout, the episode's promise in
  // `episodesDownloading` never completes and every later request for it waits forever.
  val downloadTimeout: Duration = 20.minutes

  val maxRetries: Int = 1

  // Env-only since browser profiles are machine-specific (and absent in containers). Set to e.g. "firefox" when
  // YouTube's bot detection requires cookies.
  val cookiesFromBrowserConfigKey = "PODPODGE_DOWNLOADER_COOKIES_FROM_BROWSER"

  def download(
      podcastId: PodcastId,
      videoId: String,
      downloaderPath: DownloaderPath
  ): Task[File] = {
    // TODO: Support other audio formats. "mp3" is also hardcoded in `StaticConfig.youTubeAudioPath`, the RSS
    // enclosure type, etc.
    val audioFormat = "mp3"
    val outputFile = StaticConfig.youTubeAudioPath(podcastId, videoId).toFile
    val podcastAudioDirectory = outputFile.toPath.getParent

    if (outputFile.exists) {
      ZIO.logInfo(s"${outputFile.getName} already exists. Skipping download.").as(outputFile)
    } else {
      for {
        workingDirectory <- ZIO.attempt(Files.createDirectories(podcastAudioDirectory))
        // Pass a URL rather than the bare ID since IDs can start with a hyphen, which yt-dlp parses as an option.
        videoUrl = s"https://www.youtube.com/watch?v=$videoId"
        cookiesFromBrowser <- System.env(cookiesFromBrowserConfigKey)
        _ <- runDownload(downloaderPath, audioFormat, outputFile, workingDirectory.toFile, videoUrl, cookiesFromBrowser)
               .timeoutFail(
                 new TimeoutException(s"Timed out after $downloadTimeout downloading video '$videoId'")
               )(downloadTimeout)
               // Delete partial output on failure or interruption, otherwise the `exists` check above would serve
               // a truncated file forever.
               .onExit {
                 case Exit.Success(_) => ZIO.unit
                 case _               => ZIO.attempt(outputFile.delete()).ignore
               }
               .retry(Schedule.exponential(30.seconds) && Schedule.recurs(maxRetries))
               .tapError(t =>
                 ZIO.logError(s"Giving up on video '$videoId' after ${maxRetries + 1} attempts: ${t.getMessage}")
               )
      } yield outputFile
    }
  }

  private def runDownload(
      downloaderPath: DownloaderPath,
      audioFormat: String,
      outputFile: File,
      workingDirectory: File,
      videoUrl: String,
      cookiesFromBrowser: Option[String]
  ): Task[Unit] = {
    val cookiesArgs = cookiesFromBrowser.toList.flatMap(browser => List("--cookies-from-browser", browser))

    val args = List(
      "--extract-audio",
      "--audio-format",
      audioFormat,
      // VBR can cause slowness with seeks in podcast apps, so we use a constant bitrate instead.
      "--audio-quality",
      "128K",
      "--output",
      outputFile.getName,
      // Print progress as separate lines. Without a tty, \r-based progress shows nothing until the end.
      "--newline"
    ) ++ cookiesArgs :+ videoUrl

    // Unbuffered so progress output appears immediately.
    Command(downloaderPath.unwrap, args*)
      .workingDirectory(workingDirectory)
      .env(Map("PYTHONUNBUFFERED" -> "1"))
      .inheritIO
      .successfulExitCode
      .unit
  }
}
