
package htrcagent

import httpbridge._

import akka.actor.{ Actor, ActorRef, Props }
import akka.actor.Actor._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.{ ask, pipe }
import akka.dispatch.{ Future, Await }
import java.util.Date
import scala.xml._
import scala.collection.mutable.ListBuffer

class ComputeChild(algorithmName: String, userProperties: NodeSeq, username: String, jobId: String, token: String) extends Actor {

  import context._

  val jobName = userProperties \ "job_name" text
  var results: List[AlgorithmResult] = Nil
                                  
  val propsF = AlgorithmProps(algorithmName, userProperties, username, jobId, token)
  val props = Await.result(propsF, 60 seconds)
  var status: AlgorithmStatus = Queued(props)

  val algorithm = HtrcSystem.system.actorOf(Props(new ShellAlgorithm(props, self)))

  def receive = {
    case AlgorithmStatusRequest(jobId) =>
      sender ! status
    case WorkerUpdate(newStatus) =>
      status = newStatus
    case ResultUpdate(inResults) =>
      results = inResults
    case AlgorithmStdoutRequest(inJobId) =>
      sender ! results.find { _.rtype == "stdout" }.getOrElse(EmptyResult)
    case AlgorithmStderrRequest(inJobId) =>
      sender ! results.find { _.rtype == "stderr" }.getOrElse(EmptyResult)
    case JobDirRequest(inJobId) =>
      sender ! results.find { _.rtype == "directory" }.getOrElse(EmptyResult)
  }

}
