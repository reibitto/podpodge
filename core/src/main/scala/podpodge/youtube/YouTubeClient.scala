package podpodge.youtube

import podpodge.http.Sttp
import podpodge.types.YouTubeApiKey
import sttp.client3.*
import sttp.client3.circe.*
import sttp.model.{Header, MediaType}
import zio.{Chunk, ZIO}
import zio.stream.ZStream

import java.time.Duration

object YouTubeClient {

  // Max IDs per `videos` request. Quota cost is per request, so batching saves quota.
  val MaxIdsPerRequest: Int = 50

  /** Durations keyed by video ID, for up to [[MaxIdsPerRequest]] IDs. Deleted
    * or private videos are absent from the result.
    */
  def getVideoDurations(
      videoIds: Seq[String],
      youTubeApiKey: YouTubeApiKey
  ): ZIO[Sttp, Throwable, Map[String, Duration]] =
    if (videoIds.isEmpty) ZIO.succeed(Map.empty)
    else {
      val request = basicRequest
        .get(
          uri"https://www.googleapis.com/youtube/v3/videos".withParams(
            Map(
              "key" -> youTubeApiKey.unwrap,
              "id" -> videoIds.take(MaxIdsPerRequest).mkString(","),
              "part" -> "contentDetails"
            )
          )
        )
        .headers(Header.contentType(MediaType.ApplicationJson))
        .response(asJson[VideoListResponse])

      Sttp
        .send(request)
        .map(_.body)
        .absolve
        .map(_.items.map(video => video.id -> video.contentDetails.duration).toMap)
    }

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
