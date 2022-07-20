package podpodge.server

import akka.http.scaladsl.server.Route
import podpodge.http.ApiError
import podpodge.types._
import podpodge.{ Env, PodpodgeRuntime }
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.model.StatusCode
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.{ oneOf, oneOfVariant, Codec, CodecFormat, Endpoint, EndpointOutput, Schema, SchemaType, Validator }
import zio.prelude._
import zio.{ Cause, Unsafe, ZIO }

import scala.concurrent.Future
import scala.xml.{ Elem, XML }

trait TapirSupport {
  implicit def stringNewtypeSchema[T <: RichNewtype[String]#Type]: Schema[T] = Schema(SchemaType.SString())

  implicit def stringNewtypeValidator[T <: RichNewtype[String]#Type]: Validator[T] = Validator.pass

  implicit def stringNewtypeCodec[T <: RichNewtype[String]#Type](implicit
    equiv: Equivalence[String, T]
  ): Codec[String, T, CodecFormat.TextPlain] =
    Codec.string.map(RichNewtype.wrap(_))(RichNewtype.unwrap(_))

  implicit def intSmartSchema[T <: RichNewtypeSmart[Int]#Type]: Schema[T] = Schema(SchemaType.SInteger())

  implicit def intSmartValidator[T <: RichNewtypeSmart[Int]#Type]: Validator[T] =
    // TODO: Finish this validator
    Validator.pass

  implicit def intSmartCodec[T <: RichNewtypeSmart[Int]#Type](implicit
    equiv: Equivalence[Int, T]
  ): Codec[String, T, CodecFormat.TextPlain] =
    Codec.int.map(RichNewtypeSmart.wrap(_))(RichNewtypeSmart.unwrap(_))

  implicit def longNewtypeSchema[T <: RichNewtype[Long]#Type]: Schema[T] = Schema(SchemaType.SInteger())

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

  // TODO::
  private val interpreter: AkkaHttpServerInterpreter =
    AkkaHttpServerInterpreter()(scala.concurrent.ExecutionContext.Implicits.global)

  implicit class RichZIOAkkaHttpEndpoint[I, O](endpoint: Endpoint[Unit, I, ApiError, O, AkkaStreams]) {
    def toZRoute(logic: I => ZIO[Env, Throwable, O]): Route =
      interpreter.toRoute(endpoint.serverLogic { in =>
        Unsafe.unsafe { implicit u =>
          PodpodgeRuntime.default.unsafe.runToFuture {
            logic(in).absorb
              .foldZIO(
                {
                  case e: ApiError => ZIO.left(e)
                  case t           =>
                    ZIO.logErrorCause("Unhandled error occurred", Cause.fail(t)) *>
                      ZIO.left(ApiError.InternalError("Internal server error"))
                },
                a => ZIO.right(a)
              )
          }
        }
      }: ServerEndpoint[AkkaStreams with WebSockets, Future])

  }

  val apiError: EndpointOutput.OneOf[ApiError, ApiError] = oneOf[ApiError](
    oneOfVariant(StatusCode.NotFound, jsonBody[ApiError.NotFound]),
    oneOfVariant(StatusCode.BadRequest, jsonBody[ApiError.BadRequest]),
    oneOfVariant(StatusCode.InternalServerError, jsonBody[ApiError.InternalError])
  )
}
