package podpodge.util

import sttp.model.Uri
import zio.*

import java.awt.Desktop

object Browser {

  def open(uri: Uri): Task[Unit] =
    ZIO.attempt {
      if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop.browse(uri.toJavaUri)
      }
    }
}
