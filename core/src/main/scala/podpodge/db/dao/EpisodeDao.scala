package podpodge.db.dao

import podpodge.db.Episode
import podpodge.db.Episode.Model
import podpodge.types.{EpisodeId, PodcastId}
import zio.ZIO

import java.sql.SQLException
import java.time.OffsetDateTime
import javax.sql.DataSource

object EpisodeDao extends SqlDao {
  import ctx.*

  def get(id: EpisodeId): ZIO[DataSource, SQLException, Option[Model]] =
    ctx.run {
      quote(query[Episode.Model].filter(_.id == lift(id)).take(1))
    }.map(_.headOption)

  def list: ZIO[DataSource, SQLException, List[Model]] =
    ctx.run {
      quote(query[Episode.Model])
    }

  def listExternalSource: ZIO[DataSource, SQLException, List[String]] =
    ctx.run {
      quote(query[Episode.Model].map(_.externalSource))
    }

  /** Sorted newest-first in Scala rather than via `ORDER BY`: `publish_date` is
    * stored as ISO-8601 text, which can have sortable issue whether there's
    * fractional seconds and the Z timezone, etc.
    */
  def listByPodcast(id: PodcastId): ZIO[DataSource, SQLException, List[Model]] =
    ctx.run {
      quote(query[Episode.Model].filter(_.podcastId == lift(id)))
    }
      .map(_.sortBy(_.publishDate)(Ordering[OffsetDateTime].reverse))

  def create(episode: Episode.Insert): ZIO[DataSource, SQLException, Episode[EpisodeId]] =
    ctx.run {
      quote(
        query[Episode[EpisodeId]].insertValue(lift(episode.copy(id = EpisodeId(0)))).returningGenerated(_.id)
      )
    }.map(id => episode.copy(id = id))

  def createAll(
      episodes: List[Episode.Insert]
  ): ZIO[DataSource, SQLException, List[Episode[EpisodeId]]] =
    ctx.run {
      liftQuery(episodes.map(_.copy(id = EpisodeId(0)))).foreach(e =>
        query[Episode[EpisodeId]].insertValue(e).returningGenerated(_.id)
      )
    }.map(ids => episodes.zip(ids).map { case (p, i) => p.copy(id = i) })

  def updateImage(id: EpisodeId, s: Option[String]): ZIO[DataSource, SQLException, Long] =
    ctx.run {
      query[Episode.Model].filter(_.id == lift(id)).update(_.image -> lift(s))
    }

  def updateMediaFile(id: EpisodeId, s: Option[String]): ZIO[DataSource, SQLException, Long] =
    ctx.run {
      query[Episode.Model].filter(_.id == lift(id)).update(_.mediaFile -> lift(s))
    }

  def delete(id: EpisodeId): ZIO[DataSource, SQLException, Long] =
    ctx.run {
      quote(query[Episode.Model].filter(_.id == lift(id)).delete)
    }
}
