package podpodge.db.dao

import podpodge.db.Podcast
import podpodge.db.Podcast.Model
import podpodge.types.PodcastId
import zio.ZIO

import java.sql.SQLException
import javax.sql.DataSource

object PodcastDao extends SqlDao {
  import ctx.*

  def get(id: PodcastId): ZIO[DataSource, SQLException, Option[Podcast.Model]] =
    ctx.run {
      quote(query[Podcast.Model].filter(_.id == lift(id)).take(1))
    }.map(_.headOption)

  def list: ZIO[DataSource, SQLException, List[Model]] =
    ctx.run {
      quote(query[Podcast.Model])
    }

  def create(podcast: Podcast.Insert): ZIO[DataSource, SQLException, Podcast[PodcastId]] =
    ctx.run {
      quote(query[Podcast[PodcastId]].insertValue(lift(podcast.copy(id = PodcastId(0)))).returningGenerated(_.id))
    }.map(id => podcast.copy(id = id)) // TODO: Abstract this out

  def createAll(
      podcasts: List[Podcast.Insert]
  ): ZIO[DataSource, SQLException, List[Podcast[PodcastId]]] =
    ctx.run {
      liftQuery(podcasts.map(_.copy(id = PodcastId(0)))).foreach(e =>
        query[Podcast[PodcastId]].insertValue(e).returningGenerated(_.id)
      )
    }.map(ids => podcasts.zip(ids).map { case (p, i) => p.copy(id = i) })

  def updateImage(id: PodcastId, s: Option[String]): ZIO[DataSource, SQLException, Long] =
    ctx.run {
      query[Podcast.Model].filter(_.id == lift(id)).update(_.image -> lift(s))
    }

  def delete(id: PodcastId): ZIO[DataSource, SQLException, Long] =
    ctx.run {
      quote(query[Podcast.Model].filter(_.id == lift(id)).delete)
    }

}
