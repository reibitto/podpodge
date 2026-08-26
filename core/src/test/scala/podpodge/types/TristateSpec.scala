package podpodge.types

import io.circe.{HCursor, Json}
import io.circe.parser.parse
import io.circe.syntax.*
import zio.test.*

object TristateSpec extends ZIOSpecDefault {

  def spec = suite("TristateSpec")(
    suite("decoding")(
      test("a present value decodes as Some") {
        val cursor = parse("""{"value": 5}""").toOption.get.hcursor.downField("value")
        assertTrue(cursor.as[Tristate[Int]] == Right(Tristate.Some(5)))
      },
      test("an explicit null decodes as None") {
        val cursor = parse("""{"value": null}""").toOption.get.hcursor.downField("value")
        assertTrue(cursor.as[Tristate[Int]] == Right(Tristate.None))
      },
      test("a missing field decodes as Unspecified via the regular decode path") {
        val cursor = parse("""{}""").toOption.get.hcursor.downField("value")
        assertTrue(cursor.as[Tristate[Int]] == Right(Tristate.Unspecified))
      },
      test("a missing field decodes as Unspecified via decodeAccumulating too") {
        val cursor: HCursor = HCursor.fromJson(parse("""{}""").toOption.get)
        val result = Tristate.decoder[Int].tryDecodeAccumulating(cursor.downField("value"))
        assertTrue(result.toEither == Right(Tristate.Unspecified))
      },
      test("an incorrect value type still fails to decode") {
        val cursor = parse("""{"value": "not a number"}""").toOption.get.hcursor.downField("value")
        assertTrue(cursor.as[Tristate[Int]].isLeft)
      }
    ),
    suite("encoding")(
      test("Some encodes as the raw value") {
        assertTrue((Tristate.Some(5): Tristate[Int]).asJson == Json.fromInt(5))
      },
      test("None encodes as null") {
        assertTrue((Tristate.None: Tristate[Int]).asJson == Json.Null)
      },
      test("Unspecified also encodes as null") {
        assertTrue((Tristate.Unspecified: Tristate[Int]).asJson == Json.Null)
      }
    ),
    suite("specify")(
      test("Unspecified falls back to the provided default") {
        assertTrue(Tristate.Unspecified.specify(Some(5)) == Some(5))
      },
      test("None clears regardless of the provided default") {
        assertTrue(Tristate.None.specify(Some(5)) == None)
      },
      test("Some overrides the provided default") {
        assertTrue(Tristate.Some(7).specify(Some(5)) == Some(7))
      }
    )
  )
}
