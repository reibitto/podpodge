package podpodge.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable

final case class HttpError(result: ToResponseMarshallable) extends Exception()
