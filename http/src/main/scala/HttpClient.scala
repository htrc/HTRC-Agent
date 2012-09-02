
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

  def request[R : Decoder, B : Encoder](reqType: ReqType, url: Url, body: B = Empty, headers: Iterable[(String,String)] = Nil): Future[Either[HttpError, HttpResponse[R]]] = {
    
    val r = new RequestBuilder()
    r.setUrl(url)
    r.setMethod(reqType)

    headers foreach { case (k,v) => r.setHeader(k,v) }
    
    val rb = implicitly[Encoder[B]].apply(body, r)

    val built = rb.build

    try {
      val f = makeRequest(built)
      f match {
        case Right(res) =>
          res map { r =>
            implicitly[Decoder[R]].apply(r)
          }
        case Left(e) => Future { Left(NingException(e)) }
      }
    } catch {
      case e => Future { Left(HostNotResponding(url)) }
    }
  }  

  def get[R : Decoder](url: Url) =
    request[R, Empty](GET, url)
  
  def get[R : Decoder, B : Encoder](url: Url, body: B = Empty) =
    request[R, B](GET, url, body)

  def get[R : Decoder, B : Encoder](url: Url, body: B, headers: Iterable[(String,String)]) =
    request[R, B](GET, url, body, headers)

  def get[R : Decoder](url: Url, headers: Iterable[(String,String)]) =
    request[R, Empty](GET, url, Empty, headers)
  
  def put[R : Decoder](url: Url) =
    request[R, Empty](PUT, url, Empty)

  def put[R : Decoder](url: Url, headers: Iterable[(String,String)]) =
    request[R, Empty](PUT, url, Empty, headers)

  def put[R : Decoder, B : Encoder](url: Url, body: B = Empty) =
    request[R, B](PUT, url, body)

  def put[R : Decoder, B : Encoder](url: Url, body: B, headers: Iterable[(String,String)]) = 
    request[R, B](PUT, url, body, headers)

  def post[R : Decoder, B : Encoder](url: Url, body: B) =
    request[R, B](POST, url, body)

  def delete[R : Decoder](url: Url, headers: Iterable[(String,String)]) = 
    request[R, Empty](DELETE, url, Empty, headers)

  def now[T](f: Future[T]): T = Await.result(f, 30 seconds)

}

