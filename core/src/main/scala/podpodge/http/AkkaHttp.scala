package podpodge.http

import akka.http.scaladsl.marshalling.{ ToResponseMarshallable, ToResponseMarshaller }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import podpodge.Env
import zio._

import scala.util.{ Failure, Success }

object AkkaHttp {
  implicit def toZioRoute[A: ToResponseMarshaller](zio: ZIO[Env, Throwable, A])(implicit runtime: Runtime[Env]): Route =
    zioRoute(zio)

  def zioRoute[A: ToResponseMarshaller](zio: ZIO[Env, Throwable, A])(implicit runtime: Runtime[Env]): Route = {

    val asFuture = Unsafe.unsafe { implicit u =>
      runtime.unsafe.runToFuture {
        zio.tapErrorCause(c => ZIO.logErrorCause("Unhandled internal server error", Cause.fail(c))).either
      }
    }

    onComplete(asFuture) {
      case Success(Right(value))               => complete(ToResponseMarshallable(value))
      case Success(Left(ApiError.NotFound(_))) => complete(StatusCodes.NotFound)
      case Success(Left(_)) | Failure(_)       => complete(StatusCodes.InternalServerError)
    }
  }
}
