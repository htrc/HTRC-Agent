
// decoder objects for responses to http requests

package httpbridge

import com.ning.http.client._
import play.api.libs.json._
import scala.xml._

trait Decoders {
  
  trait Decoder[T] {
    def apply(res: Response): T                                                         
  }
  
  implicit object JsonDecoder extends Decoder[JsValue] {
    def apply(res: Response): JsValue = {
      if(res.getContentType.contains("application/json"))
        Json.parse(res.getResponseBody)
      else
        JsString("error, response type not json")
    }
  }

  implicit object XmlDecoder extends Decoder[NodeSeq] {
    def apply(res: Response): NodeSeq = {
      if(res.getContentType.contains("text/xml"))
        XML.loadString(res.getResponseBody)
      else
        <error>Content-Type was not xml: {res.getContentType}</error>
    }
  }  
  
  implicit object TextDecoder extends Decoder[String] {
      def apply(res: Response): String = {
        if(res.getContentType.contains("text/plain"))
          res.getResponseBody
        else
          "error: Content-Type was not text/plain: " + res.getContentType
      }
  }

  import java.util.zip.ZipInputStream
  implicit object ZipDecoder extends Decoder[Zip] {
    def apply(res: Response): Zip = {
      if(res.getContentType.contains("application/zip")) 
        Zip(new ZipInputStream(res.getResponseBodyAsStream))
      else
        null // TODO TODO TODO GET RID OF THIS NULL!!!!!
    }
  }
  
}
