
package htrc.agent

// An actor that supervises and runs the local machine job type.

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging

class LocalMachineJob(user: HtrcUser, inputs: JobInputs, id: JobId) extends Actor {

  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  log.info("local machine job actor started")

  // The mutable state representing current status.
  val stdout = new StringBuilder
  val stderr = new StringBuilder
  var status = "Queued"

  // As a local machine shell job, we just start our child directly.
  var child: ActorRef = null
  def makeChild = actorOf(Props(new ShellTask(user, inputs, id)))  

  val behavior: PartialFunction[Any,Unit] = {
    case m: JobMessage => {
      m match {
        case JobStatusRequest(id) =>
          log.info("job status request for job: " + id)
          sender ! "JobId: " + id + " at " + status
        case StatusUpdate(newStatus) =>
          log.info("job status advanced: " + newStatus)
          status = newStatus
        case StdoutChunk(str) =>
          stdout.append(str + "\n")
        case StderrChunk(str) =>
          stderr.append(str + "\n")
        case JobOutputRequest(id, "stdout") =>
          sender ! stdout.toString
        case JobOutputRequest(id, "stderr") =>
          sender ! stderr.toString
        case JobOutputRequest(id, outputType) =>
          sender ! "unrecognized output type: " + outputType
        case RunJob =>
          log.info("launching job")
          child = makeChild
      }
    }
  }

  val unknown: PartialFunction[Any,Unit] = {
    case m =>
      log.error("job supervisor")
  }

  def receive = behavior orElse unknown

}
