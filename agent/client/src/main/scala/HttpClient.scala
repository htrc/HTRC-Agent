
// the http client level

package htrcagentclient

import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService

import com.ning.http.client._

import akka.dispatch.Future
import akka.dispatch.Await
import akka.util.duration._

import scala.xml._

class HttpClient extends Decoders with RequestTypes with Urls {

  import NingAkkaBridge._

  implicit def xmlToString(xml: NodeSeq): String = xml.toString

  def request[T : Decoder, V <% String](url: Url, body: Option[V] = None, reqType: ReqType): Future[T] = {
    
    val r = new RequestBuilder()
    r.setUrl(url)
    r.setMethod(reqType)
    if(body != None)
      r.setBody(body.get.toString)
    
    val f = makeRequest(r.build)
    f map { res: Response =>
      implicitly[Decoder[T]].apply(res)
    }
  }  

  def get[T : Decoder](url: Url) =
    request[T, String](url, None, GET)
  
  def get[T : Decoder, V <% String](url: Url, body: Option[V]) =
    request[T, V](url, body, GET)
  
  def put[T : Decoder, V <% String](url: Url, body: V) =
    request[T, V](url, Some(body), PUT)

  def now[T](f: Future[T]): T = Await.result(f, 5 seconds)

}

