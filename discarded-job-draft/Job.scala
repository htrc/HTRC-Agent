
package htrc.agent


trait Resource
trait AlgorithmType

class Job(user: HtrcUser, inputs: JobInputs, supervisor: ActorRef) extends Actor {
  this: Resource with AlgorithmType =>
    
  // A job supervisor knows about the resource type and the algorithm
  // type.
  
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  // validate parameters
  log.info("validating parameters")
  supervisor ! ValidatedParameters

  // stage locally
  log.info("staging locally")
  supervisor ! StagedLocally

  // stage remote
  log.info("staging remote")
  supervisor ! StagedRemote

  // submit to scheduler
  log.info("scheduling")
  supervisor ! Scheduled

  // queued
  log.info("queued")
  supervisor ! Queued

  // running
  log.info("running")
  supervisor ! Running

  // finished
  log.info("finished")
  supervisor ! Finished

  // cleaned up
  log.info("cleaning up")
  supervisor ! CleanedUp  

}
  
