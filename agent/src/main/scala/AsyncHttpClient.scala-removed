
package htrcagent

import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService

import com.ning.http.client._

import akka.dispatch.ExecutionContext
import akka.dispatch.Future
import akka.dispatch.Promise

object HttpClient {

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

  def get(url: String): Future[Response] = {
    val r = new RequestBuilder().setUrl(url).build()
    makeRequest(r)
  }

  def solrQuery {

    val host = "http://coffeetree.cs.indiana.edu"
    val port = 9994
    val path = "/solr/select/"
    
    val r = new RequestBuilder()
    r.setUrl(host + ":" + port + path)
    r.addQueryParameter("q", "ocr:war")

    val f = makeRequest(r.build)
    f onComplete {
      case Right(res) => println(res.getResponseBody)
      case Left(e) => println(e)
    }

  }

  def solrVolInfo(vId: String) {

    val r = new RequestBuilder()
    val qUrl = "http://coffeetree.cs.indiana.edu:9994/solr/select/"
    r.setUrl(qUrl)
    r.addQueryParameter("q", "id:" + vId)
    val f = makeRequest(r.build)
    f onComplete {
      case Right(res) => println(res.getResponseBody)
      case Left(e) => println(e)
    }
  }

  import java.util.zip.ZipInputStream

  def cassVol(vId: String) {
    val r = new RequestBuilder()
    val qUrl = "https://smoketree.cs.indiana.edu:25443/data-api/volumes"
    r.setUrl(qUrl)
    r.addQueryParameter("volumeIDs", vId)
    val f = makeRequest(r.build)
    f onComplete {
      case Right(res) => println((new ZipInputStream(res.getResponseBodyAsStream)).toString.length)
      case Left(e) => println(e)
    }
  }

}

 

