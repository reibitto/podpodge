package podpodge.db.dao

import io.getquill.{ MappedEncoding, SnakeCase, SqliteJdbcContext }
import podpodge.types._
import zio.prelude.Equivalence

import java.time.format.DateTimeFormatter
import java.time.{ Duration, OffsetDateTime }

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

  implicit def taggedIdEncoder[T <: RichNewtype[Long]#Type](implicit
    equiv: Equivalence[Long, T]
  ): MappedEncoding[T, Long] =
    MappedEncoding[T, Long](RichNewtype.unwrap(_))

  implicit def taggedIdDecoder[T <: RichNewtype[Long]#Type](implicit
    equiv: Equivalence[Long, T]
  ): MappedEncoding[Long, T] =
    MappedEncoding[Long, T](RichNewtype.wrap(_))

  implicit val sourceTypeEncoder: MappedEncoding[SourceType, String] =
    MappedEncoding[SourceType, String](_.entryName)

  implicit val sourceTypeDecoder: MappedEncoding[String, SourceType] =
    MappedEncoding[String, SourceType](SourceType.withName)
}
