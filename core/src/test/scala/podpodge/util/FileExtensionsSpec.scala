package podpodge.util

import podpodge.util.FileExtensions.*
import zio.test.*

import java.io.File

object FileExtensionsSpec extends ZIOSpecDefault {

  def spec = suite("FileExtensionsSpec")(
    test("returns the extension of a normal file") {
      assertTrue(new File("episode.mp3").extension == Some("mp3"))
    },
    test("returns the last extension when there are multiple dots") {
      assertTrue(new File("episode.final.mp3").extension == Some("mp3"))
    },
    test("returns None when there's no extension") {
      assertTrue(new File("episode").extension == None)
    },
    test("is case-preserving, not case-normalizing") {
      assertTrue(new File("episode.MP3").extension == Some("MP3"))
    }
  )
}
