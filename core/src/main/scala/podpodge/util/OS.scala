package podpodge.util

import enumeratum.{Enum, EnumEntry}

import java.util.Locale

sealed trait OS extends EnumEntry

object OS extends Enum[OS] {
  case object MacOS extends OS
  case object Windows extends OS
  case object Linux extends OS
  case object Other extends OS

  lazy val os: OS = {
    val osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)

    if (osName.contains("mac") || osName.contains("darwin"))
      OS.MacOS
    else if (osName.contains("win"))
      OS.Windows
    else if (osName.contains("nux"))
      OS.Linux
    else
      OS.Other
  }

  val values: IndexedSeq[OS] = findValues
}