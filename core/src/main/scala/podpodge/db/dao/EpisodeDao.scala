package podpodge.db.dao

import podpodge.db.Episode
import podpodge.types.{ EpisodeId, PodcastId }
import zio.Task

object EpisodeDao extends SqlDao {
  import ctx._

  def get(id: EpisodeId.Type): Task[Option[Episode.Model]] =
    Task {
      ctx.run {
        quote(query[Episode.Model].filter(_.id == lift(id)).take(1))
      }.headOption
    }

  def list: Task[List[Episode.Model]] =
    Task {
      ctx.run {
        quote(query[Episode.Model])
      }
    }

  def listExternalSource: Task[List[String]] =
    Task {
      ctx.run {
        quote(query[Episode.Model].map(_.externalSource))
      }
    }

  def listByPodcast(id: PodcastId.Type): Task[List[Episode.Model]] =
    Task {
      ctx.run {
        quote(query[Episode.Model].filter(_.podcastId == lift(id)))
      }
    }

  def create(episode: Episode.Insert): Task[Episode.Model] =
    Task {
      ctx.run {
        quote(
          query[Episode.Insert].insert(lift(episode.copy(id = EpisodeId.empty))).returningGenerated(_.id)
        )
      }
    }.map(id => episode.copy(id = id.get))

  def createAll(episodes: List[Episode.Insert]): Task[List[Episode.Model]] =
    Task {
      ctx.run {
        liftQuery(episodes.map(_.copy(id = EpisodeId.empty))).foreach(e =>
          query[Episode.Insert].insert(e).returningGenerated(_.id)
        )
      }
    }.map(ids => episodes.zip(ids).map { case (p, i) => p.copy(id = i.get) })

  def updateImage(id: EpisodeId.Type, s: Option[String]): Task[Long]       =
    Task {
      ctx.run {
        query[Episode.Model].filter(_.id == lift(id)).update(_.image -> lift(s))
      }
    }

  def delete(id: EpisodeId.Type): Task[Long] =
    Task {
      ctx.run {
        quote(query[Episode.Model].filter(_.id == lift(id)).delete)
      }
    }
}
