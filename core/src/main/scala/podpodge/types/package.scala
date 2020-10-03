package podpodge

package object types {

  // Based on scalaz's Tag implementation
  type TaggedRepr[A, T] = { type Tag = T; type Self = A }
  type @@[A, T]         = TaggedRepr[A, T]

  object Tag {
    @inline def apply[@specialized A, T](a: A): A @@ T  = a.asInstanceOf[A @@ T]
    @inline def unwrap[@specialized A, T](a: A @@ T): A = a.asInstanceOf[A]
  }

  implicit final class TagOps[A, T](private val self: A @@ T) extends AnyVal {
    def unwrap: A = Tag.unwrap(self)
  }

}
