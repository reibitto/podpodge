package podpodge

import java.io.{ File => JFile }

import podpodge.types.PodcastId
import podpodge.youtube.PlaylistItem

sealed trait CreateEpisodeRequest

object CreateEpisodeRequest {
  final case class YouTube(podcastId: PodcastId, playlistItem: PlaylistItem) extends CreateEpisodeRequest
  final case class File(podcastId: PodcastId, file: JFile)                   extends CreateEpisodeRequest
}
