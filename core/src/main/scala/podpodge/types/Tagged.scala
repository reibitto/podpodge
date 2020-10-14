package podpodge.types

trait Tagged[A, B] {
  type Value = A
  type Tag   = B
  type Type  = A @@ B

  def apply(value: A): Type  = Tag.apply[A, B](value)
  def unwrap(value: Type): A = Tag.unwrap(value)

  def modify(value: Type, f: A => A): Type = apply(f(unwrap(value)))

  def unapply(value: Type): Option[A] = Some(unwrap(value))
}

object Tagged {
  def unapply[A, B](value: A @@ B): Option[A] = Some(Tag.unwrap(value))
}

trait TaggedId[T] extends Tagged[Long, T] {
  def empty: Option[Type] = None
}
