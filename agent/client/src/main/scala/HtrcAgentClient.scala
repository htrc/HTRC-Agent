
// a client for the htrc agent that uses this http client

package htrcagentclient

import akka.dispatch.Future

import scala.xml._

class HtrcAgentClient(agentId: String) extends HttpClient {

//  val root = "http://localhost:9000/agent/" + agentId + "/"
 
//  def listAlgorithms: Future[NodeSeq] = get[NodeSeq](root + "algorithm/list")

  val root = :/("http://localhost:9000/agent/") / agentId
  def listAlgorithms = get[NodeSeq](root / "algorithm/list")

}
