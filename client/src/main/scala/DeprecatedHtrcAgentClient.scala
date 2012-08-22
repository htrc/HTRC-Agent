
// a client for the htrc agent that uses this http client

package htrcagentclient

import httpbridge._

import akka.dispatch.Future

import scala.xml._
import play.api.libs.json._

class DeprecatedHtrcAgentClient(username: String = HtrcProps.debugUser, password: String = HtrcProps.debugPass) extends HttpClient {

  var token: String = "no_token"
  lazy val auth = ("Authorization", "Bearer " + token) :: Nil
  val root = :/(HtrcProps.agentRoot)
  
  def checkErr(in: Future[Either[HttpError, HttpResponse[NodeSeq]]]): Future[Either[String,NodeSeq]] = {
    in map {
      case Left(err) => Left("decoder error: " + err.statusCode)
      case Right(res) if (res.statusCode == 200) => Right(res.body)
      case Right(res) => Left("error: " + res.statusCode)
    }
  }

  def uploadCollection(data: NodeSeq) =
    checkErr(put[NodeSeq,NodeSeq](root / "upload" / "collection", data, auth))

  val testCollection =
    <collection>
      <collection_properties>
        <e key="name">An elaborate and clever collection name</e>
        <e key="description">Some descriptive text</e>
        <e key="availability">public</e>
        <e key="tags">argle,bargle,nonsense</e>
      </collection_properties>
      <ids>
        <id>book1</id>
        <id>book2</id>
        <id>book3</id>
        <id>book4</id>
      </ids>
    </collection>

  def listAlgorithms = checkErr(get[NodeSeq](root / "algorithm" / "list", auth))
  
  def listCollections = checkErr(get[NodeSeq](root / "collection" / "list", auth))
  
  def runAlgorithm(name: String) = 
    checkErr(get[NodeSeq](root / "algorithm" / "run" / name / "foo" / "bar", auth))

  def listAgentAlgorithms = checkErr(get[NodeSeq](root / "algorithm" / "poll", auth))
  
  def pollAlgorithm(algId: String) = 
    checkErr(get[NodeSeq](root / "algorithm" / "poll" / algId, auth))

  def algorithmStdout(algId: String) =
    checkErr(get[NodeSeq](root / "algorithm" / algId / "result" / "stdout", auth))

  def algorithmStderr(algId: String) = 
    checkErr(get[NodeSeq](root / "algorithm" / algId / "result" / "stderr", auth))

  def initialize:Future[Either[String,NodeSeq]] = {
    val credentials = 
      <credentials>
        <username>{username}</username>
        <password>{password}</password>
      </credentials>
    put[NodeSeq,NodeSeq](root / "login", credentials) map {
      case Left(err) => Left("agent login failed: " + err.statusCode)
      case Right(res) if(res.statusCode == 200) => 
        token = res.body \\ "token" text
        // this newline needed due to a scala parsing bug

        Right(res.body)
      case Right(res) => Left("agent login failed: " + res.statusCode)
    }
  }

}
