import podpodge.config.Config
import podpodge.http.Sttp

import javax.sql.DataSource

package object podpodge {
  type Env = Sttp & DataSource & Config
}
