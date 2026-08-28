package podpodge.youtube

import podpodge.types.*
import podpodge.StaticConfig
import zio.*
import zio.process.Command

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeoutException

object YouTubeDL {

  // yt-dlp occasionally hangs outright instead of failing (e.g. a stalled connection, or waiting on a locked
  // browser cookie database) rather than YouTube's usual cat-and-mouse rejections, which at least fail fast. Without
  // a hard timeout, a hung process wedges its entry in `episodesDownloading` forever, since its promise never
  // completes and every future request for that episode just piles onto the same stuck promise.
  val downloadTimeout: Duration = 20.minutes

  val maxRetries: Int = 1

  // Not stored in the `configuration` table (unlike DownloaderPath) since it's meaningful only on the specific
  // machine yt-dlp runs on -- a browser profile that exists on your desktop won't exist inside a container. Unset by
  // default so containerized/headless deployments (which have no browser to read cookies from) work out of the box;
  // set this to a browser name (e.g. "firefox") on setups where YouTube's bot detection needs cookies to pass.
  val cookiesFromBrowserConfigKey = "PODPODGE_DOWNLOADER_COOKIES_FROM_BROWSER"

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
    val downloaderPath = downloaderPathOpt.getOrElse(DownloaderPath("yt-dlp"))

    if (outputFile.exists) {
      ZIO.logInfo(s"${outputFile.getName} already exists. Skipping download.").as(outputFile)
    } else {
      for {
        workingDirectory <- ZIO.attempt(Files.createDirectories(podcastAudioDirectory))
        // Pass a URL to yt-dlp instead of just the videoId because YouTube's IDs can start with a hyphen which
        // confuses yt-dlp into thinking it's a command-line option.
        videoUrl = s"https://www.youtube.com/watch?v=$videoId"
        cookiesFromBrowser <- System.env(cookiesFromBrowserConfigKey)
        _ <- runDownload(downloaderPath, audioFormat, outputFile, workingDirectory.toFile, videoUrl, cookiesFromBrowser)
               .timeoutFail(
                 new TimeoutException(s"Timed out after $downloadTimeout downloading video '$videoId'")
               )(downloadTimeout)
               // Clean up whatever yt-dlp may have partially written so a retry (or a future request) doesn't
               // mistake a broken leftover file for a completed download and skip re-downloading forever. This has
               // to cover interruption too, not just failure: the download runs on a daemon fiber, so a shutdown
               // (or the timeout above) can cut it off mid-extraction, and the `outputFile.exists` check at the top
               // would then serve that truncated mp3 forever, since nothing would ever re-download it.
               .onExit {
                 case Exit.Success(_) => ZIO.unit
                 case _               => ZIO.attempt(outputFile.delete()).ignore
               }
               .retry(Schedule.exponential(30.seconds) && Schedule.recurs(maxRetries))
               .tapError(t => ZIO.logError(s"Giving up on video '$videoId' after $maxRetries retries: ${t.getMessage}"))
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
      // Without a real terminal (as with `inheritIO` under sbt's forked run), yt-dlp collapses its \r-based
      // in-place progress updates instead of emitting them, so nothing shows until the download finishes.
      // --newline forces one full progress line per update regardless of tty detection.
      "--newline",
      videoUrl
    ) ++ cookiesArgs

    // Force yt-dlp's own stdout to flush immediately
    Command(downloaderPath.unwrap, args*)
      .workingDirectory(workingDirectory)
      .env(Map("PYTHONUNBUFFERED" -> "1"))
      .inheritIO
      .successfulExitCode
      .unit
  }
}
