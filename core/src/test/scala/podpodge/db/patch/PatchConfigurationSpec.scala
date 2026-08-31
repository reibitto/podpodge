package podpodge.db.patch

import io.circe.{Decoder, HCursor}
import io.circe.parser.parse
import podpodge.types.*
import zio.test.*

object PatchConfigurationSpec extends ZIOSpecDefault {

  private def cursorFor(json: String): HCursor = HCursor.fromJson(parse(json).toOption.get)

  private val allUnspecified = PatchConfiguration(
    Tristate.Unspecified,
    Tristate.Unspecified,
    Tristate.Unspecified,
    Tristate.Unspecified,
    Tristate.Unspecified,
    Tristate.Unspecified
  )

  def spec = suite("PatchConfigurationSpec")(
    test("a field left out of the JSON body decodes as Unspecified, not an error") {
      val result = Decoder[PatchConfiguration].decodeJson(parse("""{"downloaderPath": "yt-dlp"}""").toOption.get)
      assertTrue(result == Right(allUnspecified.copy(downloaderPath = Tristate.Some(DownloaderPath("yt-dlp")))))
    },
    test("decodeAccumulating also treats an omitted field as Unspecified") {
      val result = Decoder[PatchConfiguration].decodeAccumulating(cursorFor("""{"downloaderPath": "yt-dlp"}"""))
      assertTrue(
        result.toEither == Right(allUnspecified.copy(downloaderPath = Tristate.Some(DownloaderPath("yt-dlp"))))
      )
    },
    test("decodeAccumulating succeeds on a completely empty body, leaving every field Unspecified") {
      val result = Decoder[PatchConfiguration].decodeAccumulating(cursorFor("{}"))
      assertTrue(result.toEither == Right(allUnspecified))
    },
    test("an explicit null clears a field (None), distinct from omitting it (Unspecified)") {
      val result = Decoder[PatchConfiguration].decodeJson(parse("""{"youTubeApiKey": null}""").toOption.get)
      assertTrue(result == Right(allUnspecified.copy(youTubeApiKey = Tristate.None)))
    },
    test("all fields can be set at once") {
      val json =
        """{
          |"youTubeApiKey": "key123",
          |"serverHost": "localhost",
          |"serverPort": 8080,
          |"serverScheme": "http",
          |"downloaderPath": "yt-dlp"
          |}""".stripMargin
      val result = Decoder[PatchConfiguration].decodeJson(parse(json).toOption.get)
      assertTrue(
        result == Right(
          PatchConfiguration(
            Tristate.Some(YouTubeApiKey("key123")),
            Tristate.Some(ServerHost("localhost")),
            Tristate.Some(ServerPort.makeUnsafe(8080)),
            Tristate.Some(ServerScheme("http")),
            Tristate.Some(DownloaderPath("yt-dlp")),
            Tristate.Unspecified
          )
        )
      )
    }
  )
}
