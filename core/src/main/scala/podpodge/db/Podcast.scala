package podpodge.db

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import podpodge.types.{PodcastId, SourceType}
import podpodge.youtube.Playlist
import podpodge.StaticConfig
import sttp.client3.*
import sttp.model.Uri

import java.io.File
import java.nio.file.Path
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.util.Try

final case class Podcast[ID](
    id: ID,
    externalSource: String,
    sourceType: SourceType,
    title: String,
    description: String,
    category: String,
    generator: String,
    lastBuildDate: OffsetDateTime,
    publishDate: OffsetDateTime,
    author: String,
    subtitle: String,
    summary: String,
    image: Option[String],
    lastCheckDate: Option[OffsetDateTime]
) {

  def imagePath: Option[Path] =
    image.flatMap(name => Try(StaticConfig.coversPath.resolve(name)).toOption)

  def linkUrl: Uri = sourceType match {
    case SourceType.YouTube   => uri"https://www.youtube.com/playlist?list=$externalSource"
    case SourceType.Directory => Uri(new File(externalSource).toURI)
  }
}

object Podcast {
  type Model = Podcast[PodcastId]
  type Insert = Podcast[Unit]

  implicit val encoder: Encoder[Podcast.Model] = deriveEncoder[Podcast.Model]
  implicit val decoder: Decoder[Podcast.Model] = deriveDecoder[Podcast.Model]

  def fromPlaylist(playlist: Playlist): Podcast.Insert =
    Podcast(
      PodcastId.empty,
      playlist.id,
      SourceType.YouTube,
      playlist.snippet.title,
      playlist.snippet.description,
      "TV & Film",
      "Generated by Podpodge",
      OffsetDateTime.now,
      playlist.snippet.publishedAt,
      playlist.snippet.channelTitle,
      playlist.snippet.title,
      playlist.snippet.description,
      None,
      None
    )

  def fromDirectory(path: Path): Either[String, Podcast.Insert] = {
    val directory = path.toFile

    if (!directory.exists())
      Left(s"${directory.getAbsolutePath} does not exist")
    else if (directory.isFile)
      Left(s"${directory.getAbsolutePath} is not a directory")
    else {
      val description = s"Files of directory ${directory.getName}"

      Right(
        Podcast(
          PodcastId.empty,
          directory.toString,
          SourceType.Directory,
          directory.getName,
          description,
          "TV & Film",
          "Generated by Podpodge",
          OffsetDateTime.now,
          Instant.ofEpochMilli(directory.lastModified()).atOffset(ZoneOffset.UTC),
          System.getProperty("user.name"),
          directory.getAbsolutePath,
          description,
          None,
          None
        )
      )
    }
  }
}
