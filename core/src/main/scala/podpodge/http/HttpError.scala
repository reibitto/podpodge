package podpodge.http

import org.apache.pekko.http.scaladsl.marshalling.ToResponseMarshaller

final case class HttpError[A: ToResponseMarshaller](result: A) extends Exception(result.toString)
