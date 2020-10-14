package podpodge.db.dao

import podpodge.db.Episode
import podpodge.types.{ EpisodeId, PodcastId }
import zio.Task

object EpisodeDao extends SqlDao {
  import ctx._

  def get(id: EpisodeId): Task[Option[Episode.Model]] =
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

  def listByPodcast(id: PodcastId): Task[List[Episode.Model]] =
    Task {
      ctx.run {
        quote(query[Episode.Model].filter(_.podcastId == lift(id)))
      }
    }

  def create(episode: Episode.Insert): Task[Episode.Model] =
    Task {
      ctx.run {
        quote(
          query[Episode[EpisodeId]].insert(lift(episode.copy(id = EpisodeId(0)))).returningGenerated(_.id)
        )
      }
    }.map(id => episode.copy(id = id))

  def createAll(episodes: List[Episode.Insert]): Task[List[Episode.Model]] =
    Task {
      ctx.run {
        liftQuery(episodes.map(_.copy(id = EpisodeId(0)))).foreach(e =>
          query[Episode[EpisodeId]].insert(e).returningGenerated(_.id)
        )
      }
    }.map(ids => episodes.zip(ids).map { case (p, i) => p.copy(id = i) })

  def updateImage(id: EpisodeId, s: Option[String]): Task[Long]            =
    Task {
      ctx.run {
        query[Episode.Model].filter(_.id == lift(id)).update(_.image -> lift(s))
      }
    }

  def delete(id: EpisodeId): Task[Long] =
    Task {
      ctx.run {
        quote(query[Episode.Model].filter(_.id == lift(id)).delete)
      }
    }
}
