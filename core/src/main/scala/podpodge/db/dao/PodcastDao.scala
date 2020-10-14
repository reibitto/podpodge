package podpodge.db.dao

import podpodge.db.Podcast
import podpodge.types.PodcastId
import zio.Task

object PodcastDao extends SqlDao {
  import ctx._

  // TODO: Add a ctx.task here. Also, probably should run it in a different thread pool.
  def get(id: PodcastId): Task[Option[Podcast.Model]] =
    Task {
      ctx.run {
        quote(query[Podcast.Model].filter(_.id == lift(id)).take(1))
      }.headOption
    }

  def list: Task[List[Podcast.Model]] =
    Task {
      ctx.run {
        quote(query[Podcast.Model])
      }
    }

  def create(podcast: Podcast.Insert): Task[Podcast.Model] =
    Task {
      ctx.run {
        quote(query[Podcast[PodcastId]].insert(lift(podcast.copy(id = PodcastId(0)))).returningGenerated(_.id))
      }
    }.map(id => podcast.copy(id = id)) // TODO: Abstract this out

  def createAll(podcasts: List[Podcast.Insert]): Task[List[Podcast.Model]] =
    Task {
      ctx.run {
        liftQuery(podcasts.map(_.copy(id = PodcastId(0)))).foreach(e =>
          query[Podcast[PodcastId]].insert(e).returningGenerated(_.id)
        )
      }
    }.map(ids => podcasts.zip(ids).map { case (p, i) => p.copy(id = i) })

  def updateImage(id: PodcastId, s: Option[String]): Task[Long]            =
    Task {
      ctx.run {
        query[Podcast.Model].filter(_.id == lift(id)).update(_.image -> lift(s))
      }
    }

  def delete(id: PodcastId): Task[Long] =
    Task {
      ctx.run {
        quote(query[Podcast.Model].filter(_.id == lift(id)).delete)
      }
    }

}
