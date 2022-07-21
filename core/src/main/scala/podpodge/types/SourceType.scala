package podpodge.types

import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.LowerCamelcase

sealed trait SourceType extends EnumEntry with LowerCamelcase

object SourceType extends Enum[SourceType] with CirceEnum[SourceType] {
  case object YouTube extends SourceType
  case object Directory extends SourceType

  lazy val values: IndexedSeq[SourceType] = findValues
}
