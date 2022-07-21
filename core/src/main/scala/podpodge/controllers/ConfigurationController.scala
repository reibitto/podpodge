package podpodge.controllers

import podpodge.db.Configuration
import podpodge.db.dao.ConfigurationDao
import podpodge.db.patch.PatchConfiguration
import podpodge.types.ConfigurationId
import podpodge.{ config => _ }
import zio._

import java.sql.Connection
import javax.sql.DataSource

object ConfigurationController {
  def getPrimary: RIO[DataSource, Configuration.Model] =
    ConfigurationDao.getPrimary

  def patch(id: ConfigurationId, model: PatchConfiguration): RIO[DataSource, Configuration.Model] =
    ConfigurationDao.patch(id, model)

}
