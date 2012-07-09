
// decoder objects for responses to http requests

package htrcagentclient

import com.ning.http.client._
import play.api.libs.json._
import scala.xml._

trait Decoders {
  
  trait Decoder[T] {
    def apply(res: Response): T                                                         
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
  
}
