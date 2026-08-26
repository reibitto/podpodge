package podpodge.db

import podpodge.types.{EpisodeId, PodcastId, SourceType}
import zio.test.*

import java.nio.file.Paths
import java.time.{Duration, OffsetDateTime}

object EpisodeSpec extends ZIOSpecDefault {

  private def episode(externalSource: String): Episode.Model =
    Episode(
      id = EpisodeId(1L),
      podcastId = PodcastId(42L),
      guid = "guid",
      externalSource = externalSource,
      title = "Title",
      publishDate = OffsetDateTime.now(),
      image = None,
      mediaFile = None,
      duration = Duration.ZERO
    )

  def spec = suite("EpisodeSpec")(
    suite("mediaFilePath")(
      test("YouTube episodes resolve under the podcast's audio directory, named by video id") {
        val path = episode("dQw4w9WgXcQ").mediaFilePath(SourceType.YouTube)
        assertTrue(path == podpodge.StaticConfig.audioPath.resolve("42").resolve("dQw4w9WgXcQ.mp3"))
      },
      test("Directory episodes resolve directly to their external source path, unmodified") {
        val path = episode("/music/podcast/episode1.mp3").mediaFilePath(SourceType.Directory)
        assertTrue(path == Paths.get("/music/podcast/episode1.mp3"))
      }
    ),
    suite("linkUrl")(
      test("YouTube episodes link to the watch page for their video id") {
        val uri = episode("dQw4w9WgXcQ").linkUrl(SourceType.YouTube)
        assertTrue(uri.toString == "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
      },
      test("Directory episodes link to a file:// URI of their external source") {
        val uri = episode("/music/podcast/episode1.mp3").linkUrl(SourceType.Directory)
        assertTrue(uri.toString.startsWith("file:"), uri.toString.endsWith("/music/podcast/episode1.mp3"))
      }
    )
  )
}
