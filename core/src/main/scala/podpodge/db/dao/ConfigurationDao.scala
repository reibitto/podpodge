package podpodge.db.dao

import podpodge.db.patch.PatchConfiguration
import podpodge.db.Configuration
import podpodge.types.ConfigurationId
import zio.ZIO

import java.sql.SQLException
import javax.sql.DataSource

object ConfigurationDao extends SqlDao {
  import ctx.*

  def getPrimary: ZIO[DataSource, SQLException, Configuration.Model] =
    for {
      configOpt <- ctx.run {
                     quote(query[Configuration.Model].take(1))
                   }.map(_.headOption)
      config <- configOpt match {
                  case Some(config) => ZIO.succeed(config)
                  case None         => create(Configuration.empty)
                }
    } yield config

  def get(id: ConfigurationId): ZIO[DataSource, SQLException, Option[Configuration.Model]] =
    ctx.run {
      quote(query[Configuration.Model].filter(_.id == lift(id)).take(1))
    }.map(_.headOption)

  def create(config: Configuration.Insert): ZIO[DataSource, SQLException, Configuration.Model] =
    ctx.run {
      quote(
        query[Configuration[ConfigurationId]]
          .insertValue(lift(config.copy(id = ConfigurationId(0))))
          .returningGenerated(_.id)
      )
    }.map(id => config.copy(id = id))

  def update(model: Configuration.Model): ZIO[DataSource, SQLException, Long] =
    ctx.run {
      quote(query[Configuration.Model].filter(_.id == lift(model.id)).updateValue(lift(model)))
    }

  def patch(
      id: ConfigurationId,
      patch: PatchConfiguration
  ): ZIO[DataSource, Exception, Configuration.Model] =
    // TODO: Currently this is a hacky get + update. Fix this to programmatically generate the update statement instead.
    // I don't know how to do this with Quill though. I might need to use something else for it?
    for {
      originalConfig <-
        get(id).someOrFail(new Exception(s"Cannot patch because ConfigurationId=$id does not exist in DB"))
      patchedConfig = Configuration(
                        id = id,
                        youTubeApiKey = patch.youTubeApiKey.specify(originalConfig.youTubeApiKey),
                        serverHost = patch.serverHost.specify(originalConfig.serverHost),
                        serverPort = patch.serverPort.specify(originalConfig.serverPort),
                        serverScheme = patch.serverScheme.specify(originalConfig.serverScheme),
                        downloaderPath = patch.downloaderPath.specify(originalConfig.downloaderPath),
                        openBrowser = patch.openBrowser.specify(originalConfig.openBrowser),
                        autoCheckAllPodcastUpdates =
                          patch.autoCheckAllPodcastUpdates.specify(originalConfig.autoCheckAllPodcastUpdates)
                      )
      _ <- update(patchedConfig)
    } yield patchedConfig
}
