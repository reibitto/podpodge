package podpodge.db.dao

import podpodge.db.Episode
import podpodge.db.Episode.Model
import podpodge.types.{ EpisodeId, PodcastId }
import zio.{ Has, ZIO }

import java.sql.{ Connection, SQLException }

object EpisodeDao extends SqlDao {
  import ctx._

  def get(id: EpisodeId): ZIO[Has[Connection], SQLException, Option[Model]] =
    ctx.run {
      quote(query[Episode.Model].filter(_.id == lift(id)).take(1))
    }.map(_.headOption)

  def list: ZIO[Has[Connection], SQLException, List[Model]] =
    ctx.run {
      quote(query[Episode.Model])
    }

  def listExternalSource: ZIO[Has[Connection], SQLException, List[String]] =
    ctx.run {
      quote(query[Episode.Model].map(_.externalSource))
    }

  def listByPodcast(id: PodcastId): ZIO[Has[Connection], SQLException, List[Model]] =
    ctx.run {
      quote(query[Episode.Model].filter(_.podcastId == lift(id)))
    }

  def create(episode: Episode.Insert): ZIO[Has[Connection], SQLException, Episode[EpisodeId]] =
    ctx.run {
      quote(
        query[Episode[EpisodeId]].insert(lift(episode.copy(id = EpisodeId(0)))).returningGenerated(_.id)
      )
    }.map(id => episode.copy(id = id))

  def createAll(
    episodes: List[Episode.Insert]
  ): ZIO[Has[Connection], SQLException, List[Episode[EpisodeId]]] =
    ctx.run {
      liftQuery(episodes.map(_.copy(id = EpisodeId(0)))).foreach(e =>
        query[Episode[EpisodeId]].insert(e).returningGenerated(_.id)
      )
    }.map(ids => episodes.zip(ids).map { case (p, i) => p.copy(id = i) })

  def updateImage(id: EpisodeId, s: Option[String])               =
    ctx.run {
      query[Episode.Model].filter(_.id == lift(id)).update(_.image -> lift(s))
    }

  def updateMediaFile(id: EpisodeId, s: Option[String]): ZIO[Has[Connection], SQLException, Long] =
    ctx.run {
      query[Episode.Model].filter(_.id == lift(id)).update(_.mediaFile -> lift(s))
    }

  def delete(id: EpisodeId): ZIO[Has[Connection], SQLException, Long] =
    ctx.run {
      quote(query[Episode.Model].filter(_.id == lift(id)).delete)
    }
}
