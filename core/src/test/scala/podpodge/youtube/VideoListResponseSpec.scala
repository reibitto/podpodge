package podpodge.youtube

import io.circe.parser.decode
import zio.test.*

import java.time.Duration

object VideoListResponseSpec extends ZIOSpecDefault {

  def spec = suite("VideoListResponseSpec")(
    suite("duration decoding")(
      test("minutes and seconds") {
        assertTrue(
          decode[VideoContentDetails]("""{"duration": "PT4M13S"}""").map(_.duration) ==
            Right(Duration.ofMinutes(4).plusSeconds(13))
        )
      },
      test("hours, minutes, and seconds") {
        assertTrue(
          decode[VideoContentDetails]("""{"duration": "PT1H2M3S"}""").map(_.duration) ==
            Right(Duration.ofHours(1).plusMinutes(2).plusSeconds(3))
        )
      },
      test("a video with no duration component (e.g. a livestream) still decodes") {
        assertTrue(decode[VideoContentDetails]("""{"duration": "P0D"}""").map(_.duration) == Right(Duration.ZERO))
      },
      test("an unparseable duration fails to decode instead of throwing") {
        assertTrue(decode[VideoContentDetails]("""{"duration": "not a duration"}""").isLeft)
      }
    ),
    test("a full video list response decodes end to end") {
      val json =
        """{
          |  "items": [
          |    {"id": "abc123", "contentDetails": {"duration": "PT4M13S"}}
          |  ]
          |}""".stripMargin

      assertTrue(
        decode[VideoListResponse](json) ==
          Right(VideoListResponse(List(Video("abc123", VideoContentDetails(Duration.ofMinutes(4).plusSeconds(13))))))
      )
    }
  )
}
