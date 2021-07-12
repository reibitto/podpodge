import podpodge.config.Config
import sttp.client.httpclient.zio.SttpClient
import zio.logging.Logging
import zio.{ Has, ZEnv }

import java.sql.Connection

package object podpodge {
  type Env = ZEnv with Logging with SttpClient with Has[Connection] with Config
}
