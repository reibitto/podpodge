package podpodge.http

import org.apache.pekko.http.scaladsl.marshalling.{ToResponseMarshallable, ToResponseMarshaller}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import podpodge.Env
import zio.*

import scala.util.{Failure, Success}

object PekkoHttp {

  implicit def toZioRoute[A: ToResponseMarshaller](zio: ZIO[Env, Throwable, A])(implicit runtime: Runtime[Env]): Route =
    zioRoute(zio)

  def zioRoute[A: ToResponseMarshaller](zio: ZIO[Env, Throwable, A])(implicit runtime: Runtime[Env]): Route = {

    val asFuture = Unsafe.unsafe { implicit u =>
      runtime.unsafe.runToFuture {
        zio.tapErrorCause(c => ZIO.logErrorCause("Unhandled internal server error", c)).either
      }
    }

    onComplete(asFuture) {
      case Success(Right(value))               => complete(ToResponseMarshallable(value))
      case Success(Left(ApiError.NotFound(_))) => complete(StatusCodes.NotFound)
      case Success(Left(_)) | Failure(_)       => complete(StatusCodes.InternalServerError)
    }
  }
}
