
// a bridge between the Ning async http client and Akka futures

package httpbridge

import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService

import com.ning.http.client._

import akka.dispatch.ExecutionContext
import akka.dispatch.Future
import akka.dispatch.Promise

object NingAkkaBridge {

  val asyncHttpClient = new AsyncHttpClient()
  implicit val executorService = asyncHttpClient.getConfig.executorService
  implicit val context = ExecutionContext.fromExecutor(executorService)

  def makeRequest(r: Request):Future[Response] = {
    bridge(asyncHttpClient.prepareRequest(r).execute)
  }

  def bridge[T](fu: ListenableFuture[T]): Future[T] = { 
      val promise = Promise[T]()
      fu.addListener(new Runnable {
          def run: Unit = promise.complete(
            try Right(fu.get(10, TimeUnit.SECONDS))
            catch { case e => Left(e) }) }, executorService)
      promise
  }

}
