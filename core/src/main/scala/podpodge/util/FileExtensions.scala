package podpodge.util

import java.io.File

object FileExtensions {

  implicit class FileExtension(val self: File) extends AnyVal {

    def extension: Option[String] = {
      val name = self.getName
      val dotIndex = name.lastIndexOf(".")
      Option.when(dotIndex >= 0)(name.substring(dotIndex + 1))
    }
  }
}
