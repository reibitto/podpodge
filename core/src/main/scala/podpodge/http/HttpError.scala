package podpodge.http

import akka.http.scaladsl.marshalling.ToResponseMarshaller

final case class HttpError[A: ToResponseMarshaller](result: A) extends Exception()
