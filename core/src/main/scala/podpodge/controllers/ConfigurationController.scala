package podpodge.controllers

import podpodge.{config as _}
import podpodge.db.dao.ConfigurationDao
import podpodge.db.patch.PatchConfiguration
import podpodge.db.Configuration
import podpodge.types.ConfigurationId
import zio.*

import java.sql.Connection
import javax.sql.DataSource

object ConfigurationController {

  def getPrimary: RIO[DataSource, Configuration.Model] =
    ConfigurationDao.getPrimary

  def patch(id: ConfigurationId, model: PatchConfiguration): RIO[DataSource, Configuration.Model] =
    ConfigurationDao.patch(id, model)

}
