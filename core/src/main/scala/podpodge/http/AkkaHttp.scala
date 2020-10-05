package podpodge.http

import akka.http.scaladsl.marshalling.{ ToResponseMarshallable, ToResponseMarshaller }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import podpodge.{ Env, PodpodgeRuntime }
import zio._
import zio.logging.log

import scala.util.{ Failure, Success }

object AkkaHttp {
  implicit def toZioRoute[A: ToResponseMarshaller](zio: ZIO[Env, Throwable, A]): Route = zioRoute(zio)

  def zioRoute[E, A: ToResponseMarshaller](zio: ZIO[Env, Throwable, A]): Route = {
    val asFuture = PodpodgeRuntime.default.unsafeRunToFuture {
      zio.tapCause(c => log.error("Unhandled internal server error", c)).either
    }

    onComplete(asFuture) {
      case Success(Right(value))               => complete(ToResponseMarshallable(value))
      case Success(Left(ApiError.NotFound(_))) => complete(StatusCodes.NotFound)
      case Success(Left(_)) | Failure(_)       => complete(StatusCodes.InternalServerError)
    }
  }
}
