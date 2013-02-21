
package htrc.agent

// An implemented ComputeResource for running commands on the local
// machine.

//import akka.actor.{ Actor, Props, ActorRef }
//import akka.util.Timeout
//import scala.concurrent.duration._
//import akka.event.Logging

import akka.actor.{ Actor, ActorRef, Props }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import scala.collection.mutable.HashMap
import scala.concurrent.Future
import akka.pattern.ask

class LocalMachineResource extends ComputeResource {

  import context._

  //implicit val timeout = Timeout(30 seconds)


  //implicit val timeout = Timeout(30 seconds)

  //val log = Logging(context.system, this)

  def createJob(user: HtrcUser, inputs: JobInputs): ActorRef = {
    val newJob = system.actorOf(Props(new JobActor(user, inputs)))
    newJob
  }

}


