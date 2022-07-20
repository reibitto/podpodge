package podpodge.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import zio.{ Task, ZIO }

object DbMigration {
  def migrate: Task[MigrateResult] =
    ZIO.attempt {
      val flyway = Flyway
        .configure()
        .dataSource("jdbc:sqlite:data/podpodge.db", "", "")
        .locations("filesystem:migration")
        .load()

      flyway.migrate()
    }
}
