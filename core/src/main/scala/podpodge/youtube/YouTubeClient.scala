package podpodge.youtube

import podpodge.types.YouTubeApiKey
import sttp.client._
import sttp.client.circe.asJson
import sttp.client.httpclient.zio.SttpClient
import sttp.model.{ Header, MediaType }
import zio.Chunk
import zio.blocking.Blocking
import zio.stream.ZStream

object YouTubeClient {
  def listPlaylists(
    ids: Seq[String],
    youTubeApiKey: YouTubeApiKey
  ): ZStream[SttpClient with Blocking, Throwable, Playlist] =
    ZStream.paginateChunkM(Option.empty[String]) { pageToken =>
      val request = basicRequest
        .get(
          uri"https://www.googleapis.com/youtube/v3/playlists".withParams(
            Map(
              "key"        -> youTubeApiKey.unwrap,
              "id"         -> ids.mkString(","),
              "part"       -> "snippet,contentDetails,id",
              "maxResults" -> "50"
            ) ++ pageToken.map("pageToken" -> _).toMap
          )
        )
        .headers(Header.contentType(MediaType.ApplicationJson))
        .response(asJson[PlaylistListResponse])

      SttpClient.send(request).map(_.body).absolve.map { r =>
        (Chunk.fromIterable(r.items), r.nextPageToken.map(Some(_)))
      }
    }

  def listPlaylistItems(
    playlistId: String,
    youTubeApiKey: YouTubeApiKey
  ): ZStream[SttpClient with Blocking, Throwable, PlaylistItem] =
    ZStream
      .paginateChunkM(Option.empty[String]) { pageToken =>
        val request = basicRequest
          .get(
            uri"https://www.googleapis.com/youtube/v3/playlistItems".withParams(
              Map(
                "key"        -> youTubeApiKey.unwrap,
                "playlistId" -> playlistId,
                "part"       -> "snippet,contentDetails,id",
                "maxResults" -> "50"
              ) ++ pageToken.map("pageToken" -> _).toMap
            )
          )
          .headers(Header.contentType(MediaType.ApplicationJson))
          .response(asJson[PlaylistItemListResponse])

        SttpClient.send(request).map(_.body).absolve.map { r =>
          (Chunk.fromIterable(r.items), r.nextPageToken.map(Some(_)))
        }
      }
      .filterNot(_.isPrivate)

}
