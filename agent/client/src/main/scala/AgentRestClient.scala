
import dispatch._
import scala.xml._

object AgentClient {

  // an Http executor to carry out requests
  val http = new Http()

  // the root of the agent path
  val root = :/("localhost", 9000)

  def createAgent(user: String, x509: String, privateKey: String): Elem = {
    
    val credentials = <credentials>
                        <x509certificate>{x509}</x509certificate>
                        <privatekey>{privateKey}</privatekey>
                      </credentials>

    val req = ((root / "agent" / user) <<< credentials.toString <:< Map(("Content-type" -> "text/xml")))
    try {
      http(req <> { res => res })
    } catch {
      case e => <error>didn't work</error>
    }
  }

  def queryAgent(req: Request): Elem = {
    try {
      http(req <> { res => res})
    } catch {
      case e => <error>didn't work</error>
    }
  }

  def listAvailibleAlgorithms(user: String): Elem = {
    queryAgent(root / "agent" / user / "algorithm" / "list")
  }

  def listAvailibleCollections(user: String): NodeSeq = {
    queryAgent(root / "agent" / user / "collection" / "list")
  }

  def runAlgorithm(user: String, algName: String, colName: String, args: String): NodeSeq = {
    queryAgent(root / "agent" / user / "algorithm" / "run" / algName / colName / args)
  }

  def listAgentAlgorithms(user: String): NodeSeq = {
    queryAgent(root / "agent" / user / "algorithm" / "poll")
  }

  def algStdout(user: String, algId: String): NodeSeq = {
    queryAgent(root / "agent" / user / "algorithm" / algId / "result" / "console" / "stdout")
  }

  def algStderr(user: String, algId: String): NodeSeq = {
    queryAgent(root / "agent" / user / "algorithm" / algId / "result" / "console" / "stderr")
  }

  def algFile(user: String, algId: String, filename: String): NodeSeq = {
    queryAgent(root / "agent" / user / "algorithm" / algId / "result" / "file" / filename)
  }

  def pollAlg(user: String, algId: String): NodeSeq = {
    queryAgent(root / "agent" / user / "algorithm" / "poll" / algId)
  }
  
}
