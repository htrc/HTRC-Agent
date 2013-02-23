
package htrc.agent

// This class acts as the user inside the htrc system. The primary
// duty is the management of jobs.

import akka.actor.{ Actor, ActorRef }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import scala.collection.mutable.HashMap
import scala.concurrent.Future
import akka.pattern.ask
import akka.pattern.pipe
import scala.xml._

// parameter: the user this agent represents
class HtrcAgent(user: HtrcUser) extends Actor {

  // allow use of actor context
  import context._

  // timeout for futures
  implicit val timeout = Timeout(30 seconds)

  // jobs currently being managed
  val jobs = new HashMap[JobId, HtrcJob]

  // When a user logs in and creates this agent there will most likely
  // be saved job information in the registry. At first we are
  // assuming a small number of saved jobs, so we would like to
  // pre-fetch this information so when a user asks about old jobs the
  // information is available.
  val savedJobs = new HashMap[JobId, SavedHtrcJob]

  // logging configuration
  val log = Logging(context.system, this)

  // to add some type safety define the message types and do an
  // *exhaustive* match on those types, if not exhaustive the compiler
  // will warn due to the sealed class + explicit match block

  // thought: clean this up with some syntax? hide PF[Any,Unit] and replace
  // with something meaningful?
  
  val behavior: PartialFunction[Any,Unit] = {
    case m: AgentMessage => 
      m match {

        case SaveJob(jobId) => 
          log.info("Save job: " + jobId)
          val job = jobs.get(jobId)
          if ( job == None ) {
            sender ! <error>job: {jobId} does not exist</error>
          } else {
            job.get dispatch(m) pipeTo sender
          }

        case DeleteJob(jobId) => 
          log.info("Delete job: " + jobId)
          val job = jobs.get(jobId)
          if ( job == None ) {
            sender ! <error>job: {jobId} does not exist</error>
          } else {
            jobs -= jobId
            job.get dispatch(m) pipeTo sender
          }

        case RunAlgorithm(name, inputs) => 
          log.info("Run algorithm: " + name)
          // our magic job id from ???
          val id = JobId(HtrcUtils.newJobId)
          // get our job
          val job = 
            (HtrcSystem.jobCreator ? 
             CreateJob(new HtrcUser(name), inputs, id)).mapTo[ActorRef]
          // somehow we already have a JobId...
          jobs += (id -> HtrcJob(job))
          sender ! <elem>{"Job submitted: " + id}</elem>
          job map { j => j ! RunJob }

        case JobStatusRequest(jobId) => 
          log.info("Job status request: " + jobId)
          val job = jobs.get(jobId)
          if (job == None)
            sender ! <elem>job does not exist</elem>
          else
            (job.get dispatch m) pipeTo sender
            
        case ActiveJobStatuses => 
          log.info("Active job statuses")
          bulkJobStatus(sender)

        case SavedJobStatuses => 
          log.info("Saved job statuses")
          bulkJobStatus(sender)

        case AllJobStatuses => 
          log.info("All job statuses")
          bulkJobStatus(sender)

        case JobOutputRequest(jobId, outputType) => 
          log.info("Job output request: " + outputType)
          val job = jobs.get(jobId)
          if (job == None)
            sender ! <elem>dne</elem>
          else
            (job.get dispatch m) pipeTo sender
      }
  }

  def bulkJobStatus(sender: ActorRef) {
    val futures =
      (for( (id,job) <- jobs ) yield {
        job.ref flatMap { j =>
          j ? JobStatusRequest(id)
        }        
      }).toList
    Future.sequence(futures).mapTo[List[NodeSeq]].map { l =>
      <jobs>
        {for( j <- l ) yield j}
      </jobs>                                                       
    } pipeTo sender
  }
  
  val unknown: PartialFunction[Any,Unit] = {
    case m => 
      log.error("agent for user: " + user + " received unhandled message")
  }
    
  // now that the behavior is specified in a way such that it is
  // compiler-checked, generate the receive method

  def receive = behavior orElse unknown


}
