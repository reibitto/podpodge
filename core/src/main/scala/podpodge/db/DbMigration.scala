package podpodge.db

import org.flywaydb.core.api.output.MigrateResult
import org.flywaydb.core.Flyway
import zio.Task
import zio.ZIO

object DbMigration {

  def migrate: Task[MigrateResult] =
    ZIO.attempt {
      val flyway = Flyway
        .configure()
        .dataSource("jdbc:sqlite:data/podpodge.db", "", "")
        .locations("filesystem:data/migration")
        .load()

      flyway.migrate()
    }
}
