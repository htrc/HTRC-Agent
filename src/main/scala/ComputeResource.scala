
package htrc.agent

// The proxy representation of a compute resource. This can be
// deployed either locally or remote, but it should only exist in one
// place as it contains state.

// The JobCreator actor inspects the parameters to decide where to run
// the job. Once a decision is made the appropriate ComputeResource
// receives the message.

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import akka.pattern.ask

trait ComputeResource extends Actor {

  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system,this)

  // Somehow I need to confirm that each compute resource responds to
  // the appropriate messages. My approach will be to define an
  // initial recieve function here with abstract methods called as the
  // implementations.

  def createJob(user: HtrcUser, inputs: JobInputs, id: JobId): ActorRef

  val behavior: PartialFunction[Any,Unit] = {
    case m: ComputeResourceMessage =>
      m match {
        case CreateJob(user, inputs, id) =>
          log.info("compute resource creating job for user: " + user)
          val job = createJob(user, inputs, id)
          sender ! job
      }
  }

  val unknown: PartialFunction[Any,Unit] = {
    case m =>
      log.error("compute resource received unhandled message")
  }

  def receive = behavior orElse unknown

}


