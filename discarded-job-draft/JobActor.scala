
package htrc.agent

// A super-simple Job actor to enable development elsewhere

import akka.actor.{ Actor }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging

class JobActor(user: HtrcUser, inputs: JobInputs) extends Actor {

  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  // initialization announcement
  log.info("created job actor")

  def receive = {
    case m => log.info("job actor received: " + m)
  }

  // A job actor isn't actually a job, it watches a job. The job is
  // going to be represented as a child of some sort. As it moves
  // upwards in the lattice it will need to report changes here. 

  // An easy way to represent the child is as a Future. That way work
  // can happen asynchronously, and this actor can be monitored of
  // transitions by the completion of the futures.

  // An example is something like:
  //   parameters map { _.stageLocally; watcher ! StagedLocally }
  //              map { _.stageRemote; watcher ! StagedRemote }

  // In the case of an error we want to do something else, report the
  // failed status. To make this easy we should have a set of "ready
  // made" functions for each of these transitions.

  // parameters map { stageLocally } map { stageRemote } map ...
  
  // making this work even better would be possible by instead of
  // mapping directly on futures calling transitions directly:

  // parameters.stageLocally.stageRemote.foo

  // Now we have converted 'parameters' into a class that can turn
  // into other classes.

  // Actually implementing this is a huge pain, so I don't think this
  // is a good approach. What I will do instead is just let the job be
  // an actor that runs through the steps and messages the supervisor
  // with the appropriate updates.

  // These Job actors will be parameterized by Resource and Algorithm
  // types. 
                       
  

}
