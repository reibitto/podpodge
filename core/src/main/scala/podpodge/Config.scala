package podpodge

import java.net.{ InetAddress, NetworkInterface }
import java.nio.file.{ Files, Path, Paths }

import sttp.model.Uri
import zio.Task

object Config {
  // TODO: Turn this into a Config Service
  val apiKey: String          = sys.env.getOrElse("PODPODGE_YOUTUBE_API_KEY", "")
  val serverInterface: String = sys.env.get("PODPODGE_HOST").getOrElse("localhost")
  val serverPort: Int         = sys.env.get("PODPODGE_PORT").getOrElse("8080").toInt

  val serverScheme: String = sys.env.get("PODPODGE_SCHEME").getOrElse {
    Option(InetAddress.getByName(serverInterface)).map { address =>
      val interface = NetworkInterface.getByInetAddress(address)
      if (interface.isLoopback || interface.isVirtual || interface.isUp) "http" else "https"
    }.getOrElse("http")
  }

  val baseUri: Uri = Uri(serverScheme, serverInterface, serverPort)

  val assetsPath: Path = Paths.get("data/assets")

  // TODO: Remove the need for this folder by putting these kind of assets in `resources` instead
  val defaultAssetsPath: Path = assetsPath.resolve("default")

  val audioPath: Path      = assetsPath.resolve("audio")
  val imagesPath: Path     = assetsPath.resolve("images")
  val coversPath: Path     = imagesPath.resolve("covers")
  val thumbnailsPath: Path = imagesPath.resolve("thumbnails")

  def ensureDirectoriesExist: Task[Unit] = Task {
    Files.createDirectories(audioPath)
    Files.createDirectories(imagesPath)
    Files.createDirectories(coversPath)
    Files.createDirectories(thumbnailsPath)
  }.unit
}
