package podpodge.types

import enumeratum.EnumEntry.LowerCamelcase
import enumeratum.{ CirceEnum, Enum, EnumEntry }

sealed trait SourceType extends EnumEntry with LowerCamelcase

object SourceType extends Enum[SourceType] with CirceEnum[SourceType] {
  case object YouTube   extends SourceType
  case object Directory extends SourceType

  lazy val values: IndexedSeq[SourceType] = findValues
}
