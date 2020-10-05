package podpodge.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import podpodge.{ Env, PodpodgeRuntime }
import zio._
import zio.logging.log

import scala.util.{ Failure, Success }

object AkkaHttp {
  implicit def toZioRoute(zio: ZIO[Env, Throwable, ToResponseMarshallable]): Route = zioRoute(zio)

  // TODO: Try making this [E, A <: ToResponseMarshallable] or something to that effect.
  def zioRoute[E](zio: ZIO[Env, Throwable, ToResponseMarshallable]): Route = {
    val asFuture = PodpodgeRuntime.default.unsafeRunToFuture {
      zio.tapCause(c => log.error("Unhandled internal server error", c)).either
    }

    onComplete(asFuture) {
      case Success(Right(value))               => complete(value)
      case Success(Left(ApiError.NotFound(_))) => complete(StatusCodes.NotFound)
      case Success(Left(_)) | Failure(_)       => complete(StatusCodes.InternalServerError)
    }
  }
}
