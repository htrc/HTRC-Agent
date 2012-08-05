
// a client for the htrc agent that uses this http client

package htrcagentclient

import httpbridge._

import akka.dispatch.Future

import scala.xml._
import play.api.libs.json._

class HtrcAgentClient(agentId: String) extends HttpClient {

  var token: String = "no_token"
  lazy val auth = ("Authorization", "Bearer " + token) :: Nil
  val root = :/("http://localhost:9000/agent/")
  //val root = root2 / agentId

  def listAlgorithms = get[NodeSeq](root / "algorithm" / "list", auth)
  
  def listCollections = get[NodeSeq](root / "collection" / "list", auth)
  
  def runAlgorithm(name: String) = 
    get[NodeSeq](root / "algorithm" / "run" / name / "foo" / "bar", auth)

  def listAgentAlgorithms = get[NodeSeq](root / "algorithm" / "poll", auth)
  
  def pollAlgorithm(algId: String) = 
    get[NodeSeq](root / "algorithm" / "poll" / algId, auth)

  def algorithmStdout(algId: String) =
    get[NodeSeq](root / "algorithm" / algId / "result" / "stdout", auth)

  def algorithmStderr(algId: String) = 
    get[NodeSeq](root / "algorithm" / algId / "result" / "stderr", auth)

  def initialize:Future[NodeSeq] = {
    val credentials = 
      <credentials>
        <username>drhtrc</username>
        <password>d0ct0r.htrc</password>
      </credentials>
    val res = put[NodeSeq,NodeSeq](root / "login", credentials)
    token = (now(res) \\ "token" text)
    res
  }

  def initialize_old = {

    val credentials = 
      <credentials>
        <x509certificate>blank</x509certificate>
        <privatekey>blank</privatekey>
      </credentials>

    val res = put[NodeSeq, NodeSeq](root, credentials)
    token = now(res) \\ "token" text

    res

  }

}
