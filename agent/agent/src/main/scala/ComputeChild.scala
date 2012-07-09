
package htrcagent

import akka.actor.{ Actor, ActorRef, Props }
import akka.actor.Actor._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.{ ask, pipe }
import java.util.Date

class ComputeChild(task: RunAlgorithm, algId: String) extends Actor {

  import context._

  var status: AlgorithmStatus = Prestart(new Date, algId)
  var stdoutResult: AlgorithmResult = EmptyResult
  var stderrResult: AlgorithmResult = EmptyResult
  
  val algorithm = actorOf(Props(new ShellAlgorithm(task, algId)))

  def receive = {
    case PollAlg(algId) =>
      sender ! status
    case WorkerUpdate(newStatus) => 
      status = newStatus
    case msg: StdoutResult =>
      stdoutResult = msg
    case msg: StderrResult =>
      stderrResult = msg
    case AlgStdout(algId) =>
      sender ! stdoutResult
    case AlgStderr(algId) =>
      sender ! stderrResult

  }
  
}

