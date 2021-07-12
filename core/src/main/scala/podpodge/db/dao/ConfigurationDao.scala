package podpodge.db.dao

import podpodge.db.Configuration
import podpodge.db.patch.PatchConfiguration
import podpodge.types.ConfigurationId
import zio.{ Has, UIO, ZIO }

import java.sql.{ Connection, SQLException }

object ConfigurationDao extends SqlDao {
  import ctx._

  def getPrimary: ZIO[Has[Connection], SQLException, Configuration.Model] =
    for {
      configOpt <- ctx.run {
                     quote(query[Configuration.Model].take(1))
                   }.map(_.headOption)
      config    <- configOpt match {
                     case Some(config) => UIO(config)
                     case None         =>
                       create(
                         Configuration(
                           id = ConfigurationId.empty,
                           youTubeApiKey = None,
                           serverHost = None,
                           serverPort = None,
                           serverScheme = None
                         )
                       )
                   }
    } yield config

  def get(id: ConfigurationId): ZIO[Has[Connection], SQLException, Option[Configuration.Model]] =
    ctx.run {
      quote(query[Configuration.Model].filter(_.id == lift(id)).take(1))
    }.map(_.headOption)

  def create(config: Configuration.Insert): ZIO[Has[Connection], SQLException, Configuration.Model] =
    ctx.run {
      quote(
        query[Configuration[ConfigurationId]]
          .insert(lift(config.copy(id = ConfigurationId(0))))
          .returningGenerated(_.id)
      )
    }.map(id => config.copy(id = id))

  def update(model: Configuration.Model): ZIO[Has[Connection], SQLException, Long] =
    ctx.run {
      quote(query[Configuration.Model].filter(_.id == lift(model.id)).update(lift(model)))
    }

  def patch(
    id: ConfigurationId,
    patch: PatchConfiguration
  ): ZIO[Has[Connection], Exception, Configuration.Model] =
    // TODO: Currently this is a hacky get + update. Fix this to programmatically generate the update statement instead.
    // I don't know how to do this with Quill though. I might need to use something else for it?
    for {
      originalConfig <-
        get(id).someOrFail(new Exception(s"Cannot patch because ConfigurationId=$id does not exist in DB"))
      patchedConfig   = Configuration(
                          id = id,
                          youTubeApiKey = patch.youTubeApiKey.specify(originalConfig.youTubeApiKey),
                          serverHost = patch.serverHost.specify(originalConfig.serverHost),
                          serverPort = patch.serverPort.specify(originalConfig.serverPort),
                          serverScheme = patch.serverScheme.specify(originalConfig.serverScheme)
                        )
      _              <- update(patchedConfig)
    } yield patchedConfig
}
