
// a client for the htrc agent that uses this http client

package htrcagentclient

import httpbridge._

import akka.dispatch.Future

import scala.xml._
import play.api.libs.json._

class HtrcAgentClient(agentId: String) extends HttpClient {

  val root = :/("http://localhost:9000/agent/") / agentId

  def listAlgorithms = get[NodeSeq](root / "algorithm" / "list")
  
  def listCollections = get[NodeSeq](root / "collection" / "list")
  
  def runAlgorithm(name: String) = 
    get[NodeSeq](root / "algorithm" / "run" / name / "foo" / "bar")

  def listAgentAlgorithms = get[NodeSeq](root / "algorithm" / "poll")
  
  def pollAlgorithm(algId: String) = 
    get[NodeSeq](root / "algorithm" / "poll" / algId)

  def algorithmStdout(algId: String) =
    get[NodeSeq](root / "algorithm" / algId / "result" / "stdout")

  def algorithmStderr(algId: String) = 
    get[NodeSeq](root / "algorithm" / algId / "result" / "stderr")

  def initialize = {

    val credentials = 
      <credentials>
        <x509certificate>blank</x509certificate>
        <privatekey>blank</privatekey>
      </credentials>

    put[NodeSeq, NodeSeq](root, credentials)

  }

}