package podpodge

import podpodge.types.PodcastId
import podpodge.youtube.PlaylistItem

import java.io.File as JFile

sealed trait CreateEpisodeRequest

object CreateEpisodeRequest {
  final case class YouTube(podcastId: PodcastId, playlistItem: PlaylistItem) extends CreateEpisodeRequest
  final case class File(podcastId: PodcastId, file: JFile) extends CreateEpisodeRequest
}
