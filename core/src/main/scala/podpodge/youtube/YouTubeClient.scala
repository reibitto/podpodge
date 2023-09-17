package podpodge.youtube

import podpodge.http.Sttp
import podpodge.types.YouTubeApiKey
import sttp.client3.*
import sttp.client3.circe.*
import sttp.model.{Header, MediaType}
import zio.stream.ZStream
import zio.Chunk

object YouTubeClient {

  def listPlaylists(
      ids: Seq[String],
      youTubeApiKey: YouTubeApiKey
  ): ZStream[Sttp, Throwable, Playlist] =
    ZStream.paginateChunkZIO(Option.empty[String]) { pageToken =>
      val request = basicRequest
        .get(
          uri"https://www.googleapis.com/youtube/v3/playlists".withParams(
            Map(
              "key" -> youTubeApiKey.unwrap,
              "id" -> ids.mkString(","),
              "part" -> "snippet,contentDetails,id",
              "maxResults" -> "50"
            ) ++ pageToken.map("pageToken" -> _).toMap
          )
        )
        .headers(Header.contentType(MediaType.ApplicationJson))
        .response(asJson[PlaylistListResponse])

      Sttp.send(request).map(_.body).absolve.map { r =>
        (Chunk.fromIterable(r.items), r.nextPageToken.map(Some(_)))
      }
    }

  def listPlaylistItems(
      playlistId: String,
      youTubeApiKey: YouTubeApiKey
  ): ZStream[Sttp, Throwable, PlaylistItem] =
    ZStream
      .paginateChunkZIO(Option.empty[String]) { pageToken =>
        val request = basicRequest
          .get(
            uri"https://www.googleapis.com/youtube/v3/playlistItems".withParams(
              Map(
                "key" -> youTubeApiKey.unwrap,
                "playlistId" -> playlistId,
                "part" -> "snippet,contentDetails,id",
                "maxResults" -> "50"
              ) ++ pageToken.map("pageToken" -> _).toMap
            )
          )
          .headers(Header.contentType(MediaType.ApplicationJson))
          .response(asJson[PlaylistItemListResponse])

        Sttp.send(request).map(_.body).absolve.map { r =>
          (Chunk.fromIterable(r.items), r.nextPageToken.map(Some(_)))
        }
      }
      .filterNot(_.isPrivate)

}
