
// methods to fetch from cassandra

package httpbridge

import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService

import com.ning.http.client._

import play.api.libs.json._

import akka.dispatch.Future
import akka.dispatch.Await
import akka.util.duration._

class Cassandra(root: Url) extends HttpClient { 

  // looks like no ssl configuration is needed
  // not sure though, http client might be lying
  // (this means everything I've tried works perfectly inexplicably)

  def getVolume(token: String, id: String): Future[Either[String,Zip]] = {
    getVolumes(token, id :: Nil)
  }

  def getVolumes(token: String, ids: Traversable[String]): Future[Either[String,Zip]] = {
    val auth = ("Authorization", "Bearer " + token) :: Nil
    val url = root / "data-api" / "volumes"
    val oids = ids.mkString("|")
    val qs = ("volumeIDs",oids) :: Nil

    get[Zip, Iterable[(String,String)]](url, qs, auth) map { r => r match {
      case Left(err) => Left("data api error: " + err.statusCode)
      case Right(res) if (res.statusCode == 200) =>
        Right(res.body)
      case Right(res) =>
        Left("data api error: " + res.statusCode)
    }}
  }

  def toFolder(dir: String, zip: Zip): Zip = {
    zip.toFolder(dir)
    zip
  }
  
}
