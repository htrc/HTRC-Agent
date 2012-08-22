
// decoder objects for responses to http requests

package httpbridge

import com.ning.http.client._
import play.api.libs.json._
import scala.xml._

case class HttpResponse[T](rawResponse: Response, body: T) {
  val statusCode = rawResponse.getStatusCode
}

trait HttpError {
  val statusCode: Int
}

// this is really bad because I'm lying!
case class HostNotResponding(host: String) extends HttpError {
  val statusCode = 666
}

// more lying!
case class NingException(e: String) extends HttpError {
  val statusCode = 666
}

case class DecoderError(rawResponse: Response) extends HttpError {
  val statusCode = rawResponse.getStatusCode
}

trait Decoders {
  
  trait Decoder[T] {
    def apply(res: Response): Either[DecoderError, HttpResponse[T]]
  }
  
  implicit object JsonDecoder extends Decoder[JsValue] {
    def apply(res: Response): Either[DecoderError, HttpResponse[JsValue]] = {
      if(res.getContentType.contains("application/json"))
        Right(HttpResponse(res, Json.parse(res.getResponseBody)))
      else
        Left(DecoderError(res))
    }
  }

  implicit object XmlDecoder extends Decoder[NodeSeq] {
    def apply(res: Response): Either[DecoderError, HttpResponse[NodeSeq]] = {
      if(res.getContentType.contains("text/xml"))
        Right(HttpResponse(res, XML.loadString(res.getResponseBody)))
      else
        Left(DecoderError(res))
    }
  }  
  
  implicit object TextDecoder extends Decoder[String] {
      def apply(res: Response): Either[DecoderError, HttpResponse[String]] = {
        if(res.getContentType.contains("text/plain"))
          Right(HttpResponse(res, res.getResponseBody))
        else
          Left(DecoderError(res))
      }
  }

  import java.util.zip.ZipInputStream
  implicit object ZipDecoder extends Decoder[Zip]  {
    def apply(res: Response): Either[DecoderError, HttpResponse[Zip]] = {
      if(res.getContentType.contains("application/zip")) 
        Right(HttpResponse(res, Zip(new ZipInputStream(res.getResponseBodyAsStream))))
      else
        Left(DecoderError(res))
    }
  }
  
}
