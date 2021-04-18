package podpodge.types

import io.circe.Decoder.Result
import io.circe._

sealed trait Tristate[+A] {
  def toOption: Option[A] = this match {
    case Tristate.Unspecified | Tristate.None => None
    case Tristate.Some(x)                     => Some(x)
  }

  def specify[B >: A](specified: => Option[B]): Option[B] = this match {
    case Tristate.Unspecified => specified
    case Tristate.None        => None
    case Tristate.Some(v)     => Some(v)
  }
}

object Tristate {
  case object Unspecified        extends Tristate[Nothing]
  case object None               extends Tristate[Nothing]
  final case class Some[A](a: A) extends Tristate[A]

  implicit def encoder[A: Encoder]: Encoder[Tristate[A]] = new Encoder[Tristate[A]] {
    final def apply(a: Tristate[A]): Json = a match {
      case Tristate.Some(v)     => implicitly[Encoder[A]].apply(v)
      case Tristate.None        =>
        Json.Null
      case Tristate.Unspecified =>
        // I want to specify `Json.Absent` or something like that here, but not sure if it's possible
        Json.Null
    }
  }

  implicit def decoder[A: Decoder]: Decoder[Tristate[A]] = new Decoder[Tristate[A]] {
    final def apply(c: HCursor): Result[Tristate[A]] = tryDecode(c)

    final override def tryDecode(c: ACursor): Decoder.Result[Tristate[A]] = c match {
      case c: HCursor =>
        if (c.value.isNull) Right(Tristate.None)
        else
          implicitly[Decoder[A]].apply(c) match {
            case Right(a) => Right(Tristate.Some(a))
            case Left(df) => Left(df)
          }

      case c: FailedCursor =>
        if (!c.incorrectFocus) Right(Tristate.Unspecified)
        else Left(DecodingFailure("[A]Tristate[A]", c.history))
    }
  }
}
