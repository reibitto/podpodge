package podpodge

import podpodge.types.PodcastId
import zio.{Task, ZIO}

import java.nio.file.{Files, Path, Paths}

object StaticConfig {
  val assetsPath: Path = Paths.get("data/assets")
  val audioPath: Path = assetsPath.resolve("audio")
  val imagesPath: Path = assetsPath.resolve("images")
  val coversPath: Path = imagesPath.resolve("covers")
  val thumbnailsPath: Path = imagesPath.resolve("thumbnails")

  def youTubeAudioPath(podcastId: PodcastId, videoId: String): Path =
    audioPath.resolve(podcastId.unwrap.toString).resolve(s"$videoId.mp3")

  def ensureDirectoriesExist: Task[Unit] = ZIO.attempt {
    Files.createDirectories(audioPath)
    Files.createDirectories(imagesPath)
    Files.createDirectories(coversPath)
    Files.createDirectories(thumbnailsPath)
  }.unit
}
