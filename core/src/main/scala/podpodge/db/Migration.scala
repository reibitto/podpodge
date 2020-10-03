package podpodge.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import zio.Task

object Migration {
  def migrate: Task[MigrateResult] =
    Task {
      val flyway = Flyway
        .configure()
        .dataSource("jdbc:sqlite:data/podpodge.db", "", "")
        .locations("filesystem:migration")
        .load()
      flyway.migrate()
    }
}
