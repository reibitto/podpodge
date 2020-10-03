package podpodge.db.dao

import java.time.format.DateTimeFormatter
import java.time.{ Duration, OffsetDateTime }

import io.getquill.{ MappedEncoding, SnakeCase, SqliteJdbcContext }
import podpodge.types._

trait SqlDao extends MappedEncodings {
  val ctx: SqliteJdbcContext[SnakeCase.type] = SqlDao.ctx
}

object SqlDao {
  lazy val ctx: SqliteJdbcContext[SnakeCase.type] = new SqliteJdbcContext(SnakeCase, "ctx")
}

trait MappedEncodings {
  implicit val offsetDateTimeEncoder: MappedEncoding[OffsetDateTime, String] =
    MappedEncoding[OffsetDateTime, String](_.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))

  implicit val offsetDateTimeDecoder: MappedEncoding[String, OffsetDateTime] =
    MappedEncoding[String, OffsetDateTime](s => OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME))

  implicit val durationEncoder: MappedEncoding[Duration, String] =
    MappedEncoding[Duration, String](_.toString)

  implicit val durationDecoder: MappedEncoding[String, Duration] =
    MappedEncoding[String, Duration](Duration.parse)

  implicit def taggedIdEncoder[T]: MappedEncoding[Long @@ T, Long] =
    MappedEncoding[Long @@ T, Long](_.unwrap)

  implicit def taggedIdDecoder[T]: MappedEncoding[Long, Long @@ T] =
    MappedEncoding[Long, Long @@ T](Tag[Long, T])
}
