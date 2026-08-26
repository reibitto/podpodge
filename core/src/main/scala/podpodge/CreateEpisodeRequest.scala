package podpodge

import podpodge.types.PodcastId
import podpodge.youtube.PlaylistItem

import java.io.File as JFile
import java.time.Duration

sealed trait CreateEpisodeRequest

object CreateEpisodeRequest {

  /** @param duration
    *   resolved when the episode was enqueued, where durations for a whole page
    *   of playlist items can be fetched in one batched API call. `None` if that
    *   lookup failed or the video wasn't in the response -- duration is a
    *   nice-to-have, so it never blocks creating the episode.
    */
  final case class YouTube(podcastId: PodcastId, playlistItem: PlaylistItem, duration: Option[Duration])
      extends CreateEpisodeRequest

  final case class File(podcastId: PodcastId, file: JFile) extends CreateEpisodeRequest
}
