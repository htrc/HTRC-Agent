
// utilities to make using the http client easier

package httpbridge

import akka.dispatch.Future
import akka.dispatch.Await
import akka.util.duration._

object HttpUtils {

  def now[T](f: Future[T]): T = Await.result(f, 5 seconds)

}

