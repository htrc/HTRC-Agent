
// the http client level

package httpbridge

import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService

import com.ning.http.client._

import akka.dispatch.Future
import akka.dispatch.Await
import akka.util.duration._

import scala.xml._
import scala.collection.mutable.HashMap
import scala.collection.mutable.Map

class HttpClient extends Decoders with Encoders with RequestTypes with Urls {

  import NingAkkaBridge._

  def request[R : Decoder, B : Encoder](reqType: ReqType, url: Url, body: B = Empty, headers: Iterable[(String,String)] = Nil): Future[R] = {
    
    val r = new RequestBuilder()
    r.setUrl(url)
    r.setMethod(reqType)

    headers foreach { case (k,v) => r.setHeader(k,v) }
    
    val rb = implicitly[Encoder[B]].apply(body, r)

    val built = rb.build
    println(built.getRawUrl)
    println(built.getParams)

    val f = makeRequest(built)
    f map { res: Response =>
      implicitly[Decoder[R]].apply(res)
    }
  }  

  def get[R : Decoder](url: Url) =
    request[R, Empty](GET, url)
  
  def get[R : Decoder, B : Encoder](url: Url, body: B = Empty) =
    request[R, B](GET, url, body)

  def get[R : Decoder, B : Encoder](url: Url, body: B, headers: Iterable[(String,String)]) =
    request[R, B](GET, url, body, headers)
  
  def put[R : Decoder](url: Url) =
    request[R, Empty](PUT, url, Empty)

  def put[R : Decoder, B : Encoder](url: Url, body: B = Empty) =
    request[R, B](PUT, url, body)

  def post[R : Decoder, B : Encoder](url: Url, body: B): Future[R] =
    request[R, B](POST, url, body)

  def now[T](f: Future[T]): T = Await.result(f, 5 seconds)

}

