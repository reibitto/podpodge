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
  implicit def taggedIdSchema[T <: RichNewtype[Long]#Type]: Schema[T] = Schema(SchemaType.SInteger)

  implicit def taggedIdValidator[T <: RichNewtype[Long]#Type](implicit
    equiv: Equivalence[Long, T]
  ): Validator[T] =
    Validator.min(1L).contramap(RichNewtype.unwrap(_))

  implicit def taggedIdCodec[T <: RichNewtype[Long]#Type](implicit
    equiv: Equivalence[Long, T]
  ): Codec[String, T, CodecFormat.TextPlain] =
    Codec.long.map(RichNewtype.wrap(_))(x => RichNewtype.unwrap(x))

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
