
package htrc.agent

// Receives requests to run local machine jobs, creates the
// appropriate actor

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import akka.event.Logging
import scala.concurrent.duration._

class LocalMachineResource extends ComputeResource {
  
  // The behavior of a local machine resource is very simple, an Odin
  // resource will be much more sophisticated.

  // We simply create actors, give them the inputs, and let them run
  // shell scripts. Super simple.

  // due to the magic of generic programming all we define here is the
  // createJob function

  def createJob(user: HtrcUser, inputs: JobInputs, id: JobId): ActorRef = {


    log.info("local machine resource creating job")
    
    // create a child actor of type LocalMachineJob
    val child = context.actorOf(Props(new LocalMachineJob(user, inputs, id)), name = id.id)

    // return it?
    child

  }

}
