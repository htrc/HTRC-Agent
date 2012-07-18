
// encoder objects for content bodies

package httpbridge

import com.ning.http.client._
import play.api.libs.json._
import scala.xml._
import scala.collection.mutable.Map

trait Encoders {
  
  trait Encoder[T] {
    def apply(body: T, req: RequestBuilder): RequestBuilder
  }

  implicit object FormEncoder extends Encoder[Iterable[(String,String)]] {
    def apply(body: Iterable[(String,String)], req: RequestBuilder): RequestBuilder = {
      body foreach { case (k,v) => println(k + "=" + v); req.addQueryParameter(k,v) }
      req.addHeader("Content-Type", "application/x-www-form-urlencoded")
      req
    }
  }   
  
  implicit object XmlEncoder extends Encoder[NodeSeq] {
    def apply(body: NodeSeq, req: RequestBuilder): RequestBuilder = {
      req.setBody(body.toString)
      req.addHeader("Content-Type", "text/xml; charset=utf-8")
      req
    }
  }  
  
  implicit object TextEncoder extends Encoder[String] {
    def apply(body: String, req: RequestBuilder): RequestBuilder = {
      req.setBody(body.toString)
      req.addHeader("Content-Type", "text/plain; charset=utf-8")
      req
    }
  }

  implicit object JsonEncoder extends Encoder[JsValue] {
    def apply(body: JsValue, req: RequestBuilder): RequestBuilder = {
      req.setBody(body.toString)
      req.addHeader("Content-Type", "application/json; charset=utf-8")
      req
    }
  }

  implicit object NullBody extends Encoder[Empty] {
    def apply(body: Empty, req: RequestBuilder): RequestBuilder = {
      req
    }
  }

}

trait Empty

case object Empty extends Empty

