package podpodge.db.dao

import io.getquill.{MappedEncoding, SnakeCase, SqliteZioJdbcContext}
import podpodge.types.*
import zio.prelude.Equivalence

import java.time.Duration

trait SqlDao extends MappedEncodings {
  val ctx: SqliteZioJdbcContext[SnakeCase.type] = SqlDao.ctx
}

object SqlDao {
  lazy val ctx: SqliteZioJdbcContext[SnakeCase.type] = new SqliteZioJdbcContext(SnakeCase)
}

trait MappedEncodings {

  implicit val durationEncoder: MappedEncoding[Duration, String] =
    MappedEncoding[Duration, String](_.toString)

  implicit val durationDecoder: MappedEncoding[String, Duration] =
    MappedEncoding[String, Duration](Duration.parse)

  implicit def newtypeEncoder[A, T <: RichNewtype[A]#Type](implicit equiv: Equivalence[A, T]): MappedEncoding[T, A] =
    MappedEncoding[T, A](RichNewtype.unwrap(_))

  implicit def newtypeDecoder[A, T <: RichNewtype[A]#Type](implicit equiv: Equivalence[A, T]): MappedEncoding[A, T] =
    MappedEncoding[A, T](RichNewtype.wrap(_))

  implicit val sourceTypeEncoder: MappedEncoding[SourceType, String] =
    MappedEncoding[SourceType, String](_.entryName)

  implicit val sourceTypeDecoder: MappedEncoding[String, SourceType] =
    MappedEncoding[String, SourceType](SourceType.withName)
}
