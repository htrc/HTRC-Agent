
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
  var status: AlgorithmStatus = Prestart(new Date, jobId, jobName, username, algorithmName)
  val results = new ListBuffer[AlgorithmResult]
  var stdout: AlgorithmResult = EmptyResult
  var stderr: AlgorithmResult = EmptyResult
  var dir: AlgorithmResult = EmptyResult

  val algorithm = Algorithm(algorithmName, userProperties, username, jobId, token, self)

  def receive = {
    case AlgorithmStatusRequest(jobId) =>
      sender ! status
    case WorkerUpdate(newStatus) =>
      status = newStatus
    case msg @ StdoutResult(result) =>
      stdout = msg
      results += msg
    case msg @ StderrResult(result) =>
      stderr = msg
      results += msg
    case msg @ DirResult(result) =>
      dir = msg
      results += msg
    case ResultUpdate(result) =>
      results += result
      if(result.rtype == "stdout") 
        stdout = result
      if(result.rtype == "stderr")
        stderr = result
    case AlgorithmStdoutRequest(inJobId) =>
      sender ! stdout
    case AlgorithmStderrRequest(inJobId) =>
      sender ! stderr
    case JobDirRequest(inJobId) =>
      sender ! dir
  }

}
