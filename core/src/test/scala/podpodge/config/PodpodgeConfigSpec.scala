package podpodge.config

import podpodge.types.*
import zio.test.*

object PodpodgeConfigSpec extends ZIOSpecDefault {

  private def config(host: String, bindHost: String): PodpodgeConfig =
    PodpodgeConfig(
      youTubeApiKey = None,
      serverHost = ServerHost(host),
      serverPort = ServerPort.makeUnsafe(8080),
      serverScheme = ServerScheme("http"),
      downloaderPath = DownloaderPath("yt-dlp"),
      bindHost = ServerBindHost(bindHost)
    )

  def spec = suite("PodpodgeConfigSpec")(
    test("baseUri advertises serverHost, not the bind address") {
      // Regression test: the Docker image binds to 0.0.0.0, and baseUri is what every URL in a generated RSS feed
      // is built from. Deriving it from the bind address produced feeds full of unreachable http://0.0.0.0:8080
      // links, which no podcast app can fetch.
      val c = config(host = "podcasts.example.com", bindHost = "0.0.0.0")
      assertTrue(c.baseUri.toString == "http://podcasts.example.com:8080")
    },
    test("baseUri is unaffected by the bind address in the common case where they match") {
      val c = config(host = "localhost", bindHost = "localhost")
      assertTrue(c.baseUri.toString == "http://localhost:8080")
    }
  )
}
