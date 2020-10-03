package podpodge.youtube

import sttp.client._
import sttp.client.circe.asJson
import sttp.client.httpclient.zio.SttpClient
import sttp.model.{ Header, MediaType }
import zio.ZIO
import zio.blocking.Blocking

object YouTubeClient {
  // TODO: Use pageToken and convert to ZStream
  def listPlaylists(
    ids: Seq[String],
    apiKey: String
  ): ZIO[SttpClient with Blocking, Throwable, PlaylistListResponse] = {
    val request = basicRequest
      .get(
        uri"https://www.googleapis.com/youtube/v3/playlists".params(
          "key"        -> apiKey,
          "id"         -> ids.mkString(","),
          "part"       -> "snippet,contentDetails,id",
          "maxResults" -> "50"
        )
      )
      .headers(Header.contentType(MediaType.ApplicationJson))
      .response(asJson[PlaylistListResponse])

    SttpClient.send(request).map(_.body).absolve
  }

  // TODO: Use pageToken and convert to ZStream
  def listPlaylistItems(
    playlistId: String,
    apiKey: String
  ): ZIO[SttpClient with Blocking, Throwable, PlaylistItemListResponse] = {
    val request = basicRequest
      .get(
        uri"https://www.googleapis.com/youtube/v3/playlistItems".params(
          "key"        -> apiKey,
          "playlistId" -> playlistId,
          "part"       -> "snippet,contentDetails,id",
          "maxResults" -> "50"
        )
      )
      .headers(Header.contentType(MediaType.ApplicationJson))
      .response(asJson[PlaylistItemListResponse])

    SttpClient.send(request).map(_.body).absolve
  }
}
