package podpodge.db

import zio.test.*

import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime

object PublishDateOrderingSpec extends ZIOSpecDefault {

  private val stored = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  private val wholeSecond = OffsetDateTime.parse("2024-01-02T03:04:05Z")
  private val subSecond = OffsetDateTime.parse("2024-01-02T03:04:05.123Z") // 123ms later
  private val earlier = OffsetDateTime.parse("2024-01-02T03:04:00Z")

  def spec = suite("PublishDateOrderingSpec")(
    test("sorting by the value orders episodes newest-first") {
      val sorted = List(earlier, subSecond, wholeSecond).sortBy(identity)(Ordering[OffsetDateTime].reverse)
      assertTrue(sorted == List(subSecond, wholeSecond, earlier))
    },
    test("sorting by the stored text does NOT, which is why EpisodeDao sorts in Scala") {
      // Guards the reasoning behind not using `ORDER BY publish_date DESC`: publish_date is TEXT, and the stored
      // representation is variable-width, so SQLite's lexicographic comparison disagrees with chronological order.
      // If this ever starts passing, the encoding became fixed-width and the query could sort in SQL again.
      val lexicographic = List(earlier, subSecond, wholeSecond).map(_.format(stored)).sorted.reverse
      val chronological = List(subSecond, wholeSecond, earlier).map(_.format(stored))

      assertTrue(
        lexicographic != chronological,
        // '.' (0x2E) sorts before 'Z' (0x5A), so the later sub-second timestamp is ranked as the earlier one.
        lexicographic.head == wholeSecond.format(stored)
      )
    }
  )
}
