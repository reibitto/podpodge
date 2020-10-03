import sttp.client.httpclient.zio.SttpClient
import zio.ZEnv
import zio.logging.Logging

package object podpodge {
  type Env = ZEnv with Logging with SttpClient
}
