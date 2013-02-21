
package htrc.agent

// Responsible for creating jobs when provided user input

import akka.actor.{ Actor, Props }
import akka.util.Timeout
import akka.event.Logging
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.pipe

class JobCreator extends Actor {

  // context import
  import context._

  // timeout for futures
  implicit val timeout = Timeout(30 seconds)

  // logging
  val log = Logging(context.system, this)

  // resources
  val localMachineResource = HtrcSystem.localMachineResource

  // This actor should inspect the parameters of the incoming job to
  // figure out what resource it should be dispatched to. That
  // resource is then responsible for actually creating the job.

  val behavior: PartialFunction[Any,Unit] = {
    case m: JobCreatorMessage => 
      m match {
        case msg @ CreateJob(user, inputs, id) =>
          log.info("creating job for user: " + user)
          localMachineResource forward msg
      }
  }

  val unknown: PartialFunction[Any,Unit] = {
    case m =>
      log.error("job creator received unhandled message")
  }

  def receive = behavior orElse unknown

}
