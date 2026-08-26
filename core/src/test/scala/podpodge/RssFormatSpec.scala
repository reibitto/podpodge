package podpodge

import podpodge.rss.{Episode, Podcast}
import sttp.client3.*
import zio.test.*

import java.time.{Duration, OffsetDateTime, ZoneOffset}

object RssFormatSpec extends ZIOSpecDefault {

  private val now = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

  private def testPodcast(
      title: String = "My Podcast",
      author: String = "The Author",
      subtitle: String = "The Subtitle",
      summary: String = "The Summary",
      items: List[Episode] = Nil
  ): Podcast =
    Podcast(
      title = title,
      linkUrl = uri"https://example.com/podcast",
      selfUrl = uri"https://example.com/podcast/1/rss",
      description = "A description",
      category = "TV & Film",
      generator = "Podpodge",
      lastBuildDate = now,
      publishDate = now,
      author = author,
      subtitle = subtitle,
      summary = summary,
      imageUrl = uri"https://example.com/cover.jpg",
      items = items
    )

  private def testEpisode(
      guid: String = "abc123",
      title: String = "Episode Title",
      mediaLength: Long = 12345L
  ): Episode =
    Episode(
      downloadUrl = uri"https://example.com/episode/1/file",
      guid = guid,
      linkUrl = uri"https://www.youtube.com/watch?v=abc123",
      title = title,
      publishDate = now,
      duration = Duration.ofMinutes(4).plusSeconds(13),
      imageUrl = uri"https://example.com/thumbnail.jpg",
      mediaLength = mediaLength
    )

  def spec = suite("RssFormatSpec")(
    test("channel-level itunes:author/subtitle/summary use their own fields, not the podcast title") {
      val xml = RssFormat.encode(testPodcast()).toString
      assertTrue(
        xml.contains("<itunes:author>The Author</itunes:author>"),
        xml.contains("<itunes:subtitle>The Subtitle</itunes:subtitle>"),
        xml.contains("<itunes:summary>The Summary</itunes:summary>"),
        !xml.contains("<itunes:author>My Podcast</itunes:author>"),
        !xml.contains("<itunes:subtitle>My Podcast</itunes:subtitle>"),
        !xml.contains("<itunes:summary>My Podcast</itunes:summary>")
      )
    },
    test("item-level itunes:author uses the podcast's author, not the podcast's title") {
      val xml = RssFormat.encode(testPodcast(items = List(testEpisode()))).toString
      assertTrue(xml.contains("<itunes:author>The Author</itunes:author>"))
    },
    test("episode guid is marked isPermaLink=false since it's a video id/path, not a URL") {
      val xml = RssFormat.encode(testPodcast(items = List(testEpisode(guid = "abc123")))).toString
      assertTrue(xml.contains("""<guid isPermaLink="false">abc123</guid>"""))
    },
    test("episode description is not left empty") {
      val xml = RssFormat.encode(testPodcast(items = List(testEpisode(title = "Episode Title")))).toString
      assertTrue(xml.contains("<description>Episode Title</description>"))
    },
    test("enclosure length reflects the episode's actual media size, not a hardcoded 0") {
      val xml = RssFormat.encode(testPodcast(items = List(testEpisode(mediaLength = 999L)))).toString
      assertTrue(xml.contains("""length="999""""), !xml.contains("""length="0""""))
    },
    test("includes a self-referencing atom:link pointing at the feed's own URL") {
      val xml = RssFormat.encode(testPodcast()).toString
      assertTrue(
        xml.contains("xmlns:atom="),
        xml.contains("""href="https://example.com/podcast/1/rss""""),
        xml.contains("""rel="self"""")
      )
    },
    test("itunes:explicit uses the modern true/false form, not the deprecated yes/no") {
      val xml = RssFormat.encode(testPodcast(items = List(testEpisode()))).toString
      assertTrue(
        xml.contains("<itunes:explicit>false</itunes:explicit>"),
        !xml.contains("<itunes:explicit>no</itunes:explicit>")
      )
    },
    test("special XML characters in text fields are escaped rather than breaking the document") {
      val xml = RssFormat.encode(testPodcast(title = """Tom & Jerry's "Best" <Clips>""")).toString
      assertTrue(
        xml.contains("Tom &amp; Jerry's &quot;Best&quot; &lt;Clips&gt;"),
        !xml.contains("<Clips>")
      )
    },
    test("itunes:duration is rendered in whole seconds") {
      val xml = RssFormat.encode(testPodcast(items = List(testEpisode()))).toString
      assertTrue(xml.contains("<itunes:duration>253</itunes:duration>"))
    },
    test("an empty podcast (no episodes) still produces a valid channel with no items") {
      val xml = RssFormat.encode(testPodcast(items = Nil)).toString
      assertTrue(xml.contains("<channel>"), !xml.contains("<item>"))
    }
  )
}
