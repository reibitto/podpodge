package podpodge.db.dao

import podpodge.db.Podcast
import podpodge.types.PodcastId
import zio.Task

object PodcastDao extends SqlDao {
  import ctx._

  // TODO: Add a ctx.task here. Also, probably should run it in a different thread pool.
  def get(id: PodcastId.Type): Task[Option[Podcast.Model]] =
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
        quote(query[Podcast.Insert].insert(lift(podcast.copy(id = PodcastId.empty))).returningGenerated(_.id))
      }
    }.map(id => podcast.copy(id = id.get)) // TODO: Abstract this out and avoid `.get`

  def createAll(podcasts: List[Podcast.Insert]): Task[List[Podcast.Model]] =
    Task {
      ctx.run {
        liftQuery(podcasts.map(_.copy(id = PodcastId.empty))).foreach(e =>
          query[Podcast.Insert].insert(e).returningGenerated(_.id)
        )
      }
    }.map(ids => podcasts.zip(ids).map { case (p, i) => p.copy(id = i.get) })
  // TODO: Abstract this out and avoid `.get`

  def updateImage(id: PodcastId.Type, s: Option[String]): Task[Long] =
    Task {
      ctx.run {
        query[Podcast.Model].filter(_.id == lift(id)).update(_.image -> lift(s))
      }
    }

  def delete(id: PodcastId.Type): Task[Long] =
    Task {
      ctx.run {
        quote(query[Podcast.Model].filter(_.id == lift(id)).delete)
      }
    }

}
