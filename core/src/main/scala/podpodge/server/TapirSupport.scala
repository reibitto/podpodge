package podpodge.server

import akka.http.scaladsl.server.Route
import podpodge.http.ApiError
import podpodge.types._
import podpodge.{ Env, PodpodgeRuntime }
import sttp.capabilities.akka.AkkaStreams
import sttp.model.StatusCode
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.akkahttp.{ AkkaHttpServerOptions, RichAkkaHttpEndpoint }
import sttp.tapir.{ oneOf, statusMapping, Codec, CodecFormat, Endpoint, Schema, SchemaType, Validator }
import zio.logging.log
import zio.prelude._
import zio.{ UIO, ZIO }

import scala.xml.{ Elem, XML }

trait TapirSupport {
  implicit def stringNewtypeSchema[T <: RichNewtype[String]#Type]: Schema[T] = Schema(SchemaType.SString)

  implicit def stringNewtypeValidator[T <: RichNewtype[String]#Type]: Validator[T] = Validator.pass

  implicit def stringNewtypeCodec[T <: RichNewtype[String]#Type](implicit
    equiv: Equivalence[String, T]
  ): Codec[String, T, CodecFormat.TextPlain] =
    Codec.string.map(RichNewtype.wrap(_))(RichNewtype.unwrap(_))

  implicit def intSmartSchema[T <: RichNewtypeSmart[Int]#Type]: Schema[T] = Schema(SchemaType.SInteger)

  implicit def intSmartValidator[T <: RichNewtypeSmart[Int]#Type]: Validator[T] =
    // TODO: Finish this validator
    Validator.pass

  implicit def intSmartCodec[T <: RichNewtypeSmart[Int]#Type](implicit
    equiv: Equivalence[Int, T]
  ): Codec[String, T, CodecFormat.TextPlain] =
    Codec.int.map(RichNewtypeSmart.wrap(_))(RichNewtypeSmart.unwrap(_))

  implicit def longNewtypeSchema[T <: RichNewtype[Long]#Type]: Schema[T] = Schema(SchemaType.SInteger)

  implicit def longNewtypeValidator[T <: RichNewtype[Long]#Type](implicit
    equiv: Equivalence[Long, T]
  ): Validator[T] =
    Validator.min(1L).contramap(RichNewtype.unwrap(_))

  implicit def longNewtypeCodec[T <: RichNewtype[Long]#Type](implicit
    equiv: Equivalence[Long, T]
  ): Codec[String, T, CodecFormat.TextPlain] =
    Codec.long.map(RichNewtype.wrap(_))(RichNewtype.unwrap(_))

  implicit val xmlCodec: Codec[String, Elem, CodecFormat.Xml] =
    implicitly[PlainCodec[String]].map(XML.loadString(_))(_.toString).format(CodecFormat.Xml())

  implicit class RichZIOAkkaHttpEndpoint[I, O](endpoint: Endpoint[I, ApiError, O, AkkaStreams])(implicit
    serverOptions: AkkaHttpServerOptions
  ) {
    def toZRoute(logic: I => ZIO[Env, Throwable, O]): Route =
      endpoint.toRoute { in =>
        PodpodgeRuntime.unsafeRunToFuture {
          logic(in).absorb
            .foldM(
              {
                case e: ApiError => UIO.left(e)
                case t           =>
                  log.throwable("Unhandled error occurred", t) *>
                    UIO.left(ApiError.InternalError("Internal server error"))
              },
              a => UIO.right(a)
            )
        }
      }
  }

  val apiError = oneOf[ApiError](
    statusMapping(StatusCode.NotFound, jsonBody[ApiError.NotFound]),
    statusMapping(StatusCode.BadRequest, jsonBody[ApiError.BadRequest]),
    statusMapping(StatusCode.InternalServerError, jsonBody[ApiError.InternalError])
  )
}
