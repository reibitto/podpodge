package podpodge.types

import zio.test.*

object TypesSpec extends ZIOSpecDefault {

  def spec = suite("TypesSpec")(
    suite("ServerPort")(
      test("accepts the minimum valid port (0)") {
        assertTrue(ServerPort.make(0).toOption.isDefined)
      },
      test("accepts a typical port (8080)") {
        assertTrue(ServerPort.make(8080).toOption.isDefined)
      },
      test("accepts the maximum valid port (65535)") {
        assertTrue(ServerPort.make(65535).toOption.isDefined)
      },
      test("rejects a port above the valid range (65536)") {
        assertTrue(ServerPort.make(65536).toOption.isEmpty)
      },
      test("rejects a negative port") {
        assertTrue(ServerPort.make(-1).toOption.isEmpty)
      }
    )
  )
}
