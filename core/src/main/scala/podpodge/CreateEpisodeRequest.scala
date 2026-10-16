package podpodge

import podpodge.types.PodcastId
import podpodge.youtube.PlaylistItem

import java.io.File as JFile
import java.time.Duration

sealed trait CreateEpisodeRequest

object CreateEpisodeRequest {

  /** @param duration
    *   fetched in batches when enqueuing. `None` if the lookup failed, which
    *   doesn't block episode creation.
    */
  final case class YouTube(podcastId: PodcastId, playlistItem: PlaylistItem, duration: Option[Duration])
      extends CreateEpisodeRequest

  final case class File(podcastId: PodcastId, file: JFile) extends CreateEpisodeRequest
}
