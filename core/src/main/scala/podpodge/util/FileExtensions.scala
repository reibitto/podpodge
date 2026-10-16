package podpodge.util

import java.io.File

object FileExtensions {

  implicit class FileExtension(val self: File) extends AnyVal {

    def extension: Option[String] = {
      val name = self.getName
      val dotIndex = name.lastIndexOf(".")
      Option.when(dotIndex >= 0)(name.substring(dotIndex + 1))
    }

    // Subtype of the file's audio MIME type, e.g. "mpeg" for audio/mpeg. Falls back to mpeg for unknown extensions.
    def audioMediaSubtype: String = extension.map(_.toLowerCase) match {
      case Some("m4a") => "x-m4a"
      case Some("ogg") => "ogg"
      case _           => "mpeg"
    }
  }
}
