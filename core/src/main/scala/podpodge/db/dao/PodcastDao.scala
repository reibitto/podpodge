package podpodge.db.dao

import podpodge.db.Podcast
import podpodge.db.Podcast.Model
import podpodge.types.PodcastId
import zio.{ Has, ZIO }

import java.sql.{ Connection, SQLException }

object PodcastDao extends SqlDao {
  import ctx._

  def get(id: PodcastId): ZIO[Has[Connection], SQLException, Option[Podcast.Model]] =
    ctx.run {
      quote(query[Podcast.Model].filter(_.id == lift(id)).take(1))
    }.map(_.headOption)

  def list: ZIO[Has[Connection], SQLException, List[Model]] =
    ctx.run {
      quote(query[Podcast.Model])
    }

  def create(podcast: Podcast.Insert): ZIO[Has[Connection], SQLException, Podcast[PodcastId]] =
    ctx.run {
      quote(query[Podcast[PodcastId]].insert(lift(podcast.copy(id = PodcastId(0)))).returningGenerated(_.id))
    }.map(id => podcast.copy(id = id)) // TODO: Abstract this out

  def createAll(
    podcasts: List[Podcast.Insert]
  ): ZIO[Has[Connection], SQLException, List[Podcast[PodcastId]]] =
    ctx.run {
      liftQuery(podcasts.map(_.copy(id = PodcastId(0)))).foreach(e =>
        query[Podcast[PodcastId]].insert(e).returningGenerated(_.id)
      )
    }.map(ids => podcasts.zip(ids).map { case (p, i) => p.copy(id = i) })

  def updateImage(id: PodcastId, s: Option[String]): ZIO[Has[Connection], SQLException, Long] =
    ctx.run {
      query[Podcast.Model].filter(_.id == lift(id)).update(_.image -> lift(s))
    }

  def delete(id: PodcastId): ZIO[Has[Connection], SQLException, Long] =
    ctx.run {
      quote(query[Podcast.Model].filter(_.id == lift(id)).delete)
    }

}
