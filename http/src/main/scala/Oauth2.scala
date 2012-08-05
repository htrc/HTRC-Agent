
// methods to perform oauth2 authentication

package httpbridge

import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService

import com.ning.http.client._

import play.api.libs.json._

import akka.dispatch.Future
import akka.dispatch.Await
import akka.util.duration._

class Oauth2(root: Url) extends HttpClient { 

  // looks like no ssl configuration is needed
  // not sure though, http client might be lying

  def authenticate(name: String, pass: String): Future[Oauth2Token] = {

    val credentials = 
      ("grant_type" -> "client_credentials") ::
      ("client_id" -> name) ::
      ("client_secret" -> pass) ::
      Nil
   
    val response: Future[JsValue] = post[JsValue, Iterable[(String,String)]](root, credentials)

    response map { js =>
      val token = (js \ "access_token").as[String]
      val expires_in = (js \ "expires_in").as[String].toLong
      Oauth2Token(token, System.currentTimeMillis+expires_in, name)
    }

  }
  
}

case class Oauth2Token(token: String, expirationTime: Long, username: String)
