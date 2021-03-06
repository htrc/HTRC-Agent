
/*
#
# Copyright 2013 The Trustees of Indiana University
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
*/


package htrc.agent

// This class acts as the user inside the htrc system. The primary
// duty is the management of jobs.

import akka.actor.{ Actor, ActorRef }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import akka.event.slf4j.Logger
import scala.collection.mutable.HashMap
import scala.concurrent.Future
import akka.pattern.ask
import akka.pattern.pipe
import scala.xml._
import scala.collection.mutable.MutableList

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
  // whether the list of saved jobs is available
  var savedJobsReady = false

  // whether the list of saved jobs is currently being retrieved
  var processingSavedJobs = false

  // lists of actors that have sent either "SavedJobStatuses" or
  // "AllJobStatuses", and are still waiting for responses
  val waitListForSavedJobs = new MutableList[ActorRef]
  val waitListForAllJobs = new MutableList[ActorRef]

  // logging configuration
  val log = Logging(context.system, this)
  val auditLog = Logger("audit")

  override def preStart() = {
    log.debug("HtrcAgent({}) starting", user.name)
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error(reason, "HtrcAgent({}) restarting due to [{}] when processing [{}]", user.name, reason.getMessage, message.getOrElse(""))
  }

  // to add some type safety define the message types and do an
  // *exhaustive* match on those types, if not exhaustive the compiler
  // will warn due to the sealed class + explicit match block

  // thought: clean this up with some syntax? hide PF[Any,Unit] and replace
  // with something meaningful?

  val behavior: PartialFunction[Any,Unit] = {
    case m: AgentMessage => 
      m match {

        case SaveJob(jobId, token) => 
          val job = jobs.get(jobId)
          if ( job == None) {
            sender ! <error>job: {jobId} does not exist</error>
          } else {
            // job.get dispatch(m) pipeTo sender
            job.get.status match {
              case s: JobComplete =>
                val f = RegistryHttpClient.saveJob(s, jobId.toString, token)
                f map {res => 
                  self ! JobSaveCompleted(jobId, s, res)
                  if (res) 
                    sender ! <job>Saved job</job>
                  else
                    sender ! <error>Error while trying to to save job.</error>
                }
              case s => 
                sender ! <error>Job not yet finished. Failed to save.</error>
            }
          }

        case DeleteJob(jobId, token) => 
          val job = jobs.get(jobId)
          val savedJob = savedJobs.get(jobId)
          if ( job == None && savedJob == None ) {
            sender ! <error>job: {jobId} does not exist</error>
          } else {
            if( savedJob != None ) {
              val currSender = sender
              RegistryHttpClient.deleteJob(jobId.toString, token) map { b =>
                if (b) {
                  savedJobs -= jobId
                  currSender ! <success>deleted job: {jobId}</success>
                } else {
                  currSender ! <error>Job {jobId} not deleted</error>
                }
              }
            }
            if( job != None ) {
              jobs -= jobId
              job.get dispatch(m) pipeTo sender
            }
          }

        case RunAlgorithm(inputs) =>  

          // we want to check if our naive throttling is active and
          // rejecting jobs
          if( HtrcConfig.jobThrottling && !JobThrottler.jobsOk ) {
            log.info("Rejecting job due to overloading")
            sender ! <error>System has exceed maximum active job count, please try again later.</error>
          } else {

            JobThrottler.addJob()
            
            // our magic job id from ???
            val id = JobId(HtrcUtils.newJobId)
            
            // for audit log anaylzer
            // type request_id user ip token job_id job_name algorithm
            val fstr = "JOB_SUBMISSION\t%s\t%s\t%s\t%s\t%s\t%s\t%s"
            auditLog.info(fstr.format(inputs.requestId, user.name, inputs.ip, 
                                 inputs.token, id.toString,
                                 inputs.name, inputs.algorithm))
            
            // get our job
            val job = 
              (HtrcSystem.jobCreator ? 
               CreateJob(user, inputs, id)).mapTo[ActorRef]
            // somehow we already have a JobId...
            val jobStatus = Queued(inputs, id)
            jobs += (id -> HtrcJob(job, jobStatus))
            sender ! Queued(inputs, id).renderXml
            job map { j => j ! RunJob }
          }

        // msg to create a job for this user based on existing job results in
        // the cache
	case CreateJobFromCache(inputs, cachedJobId) =>
	  if( HtrcConfig.jobThrottling && !JobThrottler.jobsOk ) {
	    log.info("Rejecting job due to overloading")
	    sender ! <error>Max job count exceeded. Please try again later.</error>
	  } else {
	    JobThrottler.addJob() 
	    val id = JobId(HtrcUtils.newJobId)
    
	    // for audit log anaylzer
	    // type request_id user ip token job_id job_name algorithm
	    val fstr = "CACHED_JOB_SUBMISSION\t%s\t%s\t%s\t%s\t%s\t%s\t%s"
	    auditLog.info(fstr.format(inputs.requestId, user.name, inputs.ip, 
				      inputs.token, id.toString,
				      inputs.name, inputs.algorithm))

	    val job = 
	      (HtrcSystem.jobCreator ? 
	       CreateCachedJob(inputs, id, cachedJobId)).mapTo[ActorRef]
	    val jobStatus = Queued(inputs, id)
	    jobs += (id -> HtrcJob(job, jobStatus))
	    sender ! jobStatus.renderXml
	    job map { j => j ! RunJob }
	  }

        case JobStatusRequest(jobId) => 
          val job = jobs.get(jobId)
          val savedJob = savedJobs.get(jobId)
          if (job == None && savedJob == None) {
            sender ! <elem>job does not exist</elem>
          } else if(savedJob != None) {
            sender ! savedJob.get.renderXml
          } else {
            // (job.get dispatch m) pipeTo sender
            sender ! job.get.status.renderXml
          }
            
        case ActiveJobStatuses => 
          // bulkJobStatus(sender)
          activeJobStatus(sender)

        // message sent from a Future launched by this actor, in which saved
        // jobs are retrieved, to itself
        case SavedJobs(seq) =>
          savedJobsReady = true
          processingSavedJobs = false
          seq.foreach(j => savedJobs += (JobId(j.id) -> j))
          val saved = Some(savedJobs.values.toList)
          waitListForSavedJobs foreach { a =>
            a ! <jobs>{for(j <- savedJobs.values) yield j.renderXml}</jobs>
          }
          waitListForAllJobs foreach { a => bulkJobStatus(a, saved) }

        case SavedJobStatuses(token) =>
          if (savedJobsReady) {
            val saved = Some(savedJobs.values.toList)
            sender ! <jobs>{for(j <- savedJobs.values) yield j.renderXml}</jobs>
          } else {
            if (processingSavedJobs) {
              waitListForSavedJobs += sender
            } else {
              // if the list of saved jobs is not available, start retrieving
              // them; if there are any messages to this actor during
              // retrieval, the actor knows that retrieval is underway
              // through processingSavedJobs
              processingSavedJobs = true
              waitListForSavedJobs += sender
              // get the list of all saved jobs
              val f = RegistryHttpClient.listSavedJobs(token)
              f flatMap { names =>
                // get the file for each of the saved jobs
                RegistryHttpClient.downloadSavedJobs(names, token)
              } foreach { seq =>
                // once all jobs have been retrieved, send a SavedJobs
                // message from the Future in which this executes, to this
                // actor
                self ! SavedJobs(seq)
              }
            }
          }

        case AllJobStatuses(token) =>
          if (savedJobsReady) {
            val saved = Some(savedJobs.values.toList)
            bulkJobStatus(sender, saved)
          } else {
            if (processingSavedJobs) {
              waitListForAllJobs += sender
            } else {
              // if the list of saved jobs is not available, start retrieving
              // them; if there are any messages to this actor during
              // retrieval, the actor knows that retrieval is underway
              // through processingSavedJobs
              processingSavedJobs = true
              waitListForAllJobs += sender
              // get the list of all saved jobs
              val f = RegistryHttpClient.listSavedJobs(token)
              f flatMap { names =>
                // get the file for each of the saved jobs
                RegistryHttpClient.downloadSavedJobs(names, token)
              } foreach { seq =>
                // once all jobs have been retrieved, send a SavedJobs
                // message from the Future in which this executes, to this
                // actor
                self ! SavedJobs(seq)
              }
            }
          }

        // temporarily commented out; should be uncommented when stderr,
        // stdout etc. are available for the job
        // case JobOutputRequest(jobId, outputType) => 
        //   val job = jobs.get(jobId)
        //   if (job == None)
        //     sender ! <elem>dne</elem>
        //   else
        //     (job.get dispatch m) pipeTo sender

        // UpdateJobStatus is received here after a msg is received by the
        // agent from an instance of AgentJobClient
        case updateStatus @ UpdateJobStatus(jobId, tok, newStatus) => 
          log.info("UpdateJobStatus(" + jobId + ", " + newStatus + 
                    ") received by HtrcAgent(" + user + ")")

          val successMsg = <success>Update of job status successful.</success>
          val errorMsg = <error>Error in update of job status: Non-existent jobId {jobId}.</error>

          val job = jobs.get(jobId)
          val res = job map {j => 
            self ! InternalUpdateJobStatus(jobId, JobStatus(j.status, newStatus), 
                                           tok)
            successMsg
          } getOrElse { 
            log.error("ERROR: HtrcAgent({}) received UpdateJobStatus for " + 
                      "non-existent job\tJOB_ID: {}\tSTATUS: {}",
                      user.name, jobId, newStatus)
            errorMsg
          }
          sender ! res

        // InternalUpdateJobStatus msgs are received from this actor or from
        // other actors such as LocalMachineJob, JobCompletionTask
        case InternalUpdateJobStatus(jobId, status, token) => 
          log.debug("InternalUpdateJobStatus(" + jobId + ", " + status + ", " +
                    token + ") received by HtrcAgent(" + user + ")")
          val job = jobs.get(jobId)
          if (job == None)
            log.error("ERROR: HtrcAgent({}) received InternalUpdateJobStatus " + 
                      "for non-existent job\tJOB_ID: {}\tSTATUS: {}",
                      user.name, jobId, status)
          else
            status match {
              case s: PendingCompletion => 
                JobCompletionTask(s, token, context) 
	      case s: JobComplete => 
	        s match {
		  case fin: Finished => 
		    fin.inputs.jobResultCacheKey foreach { cacheKey =>
		      HtrcSystem.cacheController ! AddJobToCache(cacheKey, fin)
		    }
		    handleCompletedJobs(jobId, s, token)
		  case _ => handleCompletedJobs(jobId, s, token)
		}
              case _ => job.get.setStatus(status)
            }

        // JobSaveCompleted is sent by HtrcAgent to itself once the registry
        // call to save a job with status JobComplete has been completed;
        // there are 2 kinds of "job saves": (a) jobs are automatically saved
        // to the registry once they are completed, (b) in case of jobs where
        // (a) fails, the user may attempt to explicitly save the job; a
        // JobSaveCompleted msg may be received by HtrcAgent in both
        // situations, (a) and (b)
        case JobSaveCompleted(jobId, status, saveResult) => 
          if (saveResult)  {
            savedJobs += (jobId -> (new SavedHtrcJob(status)))
            jobs -= jobId
          }
          else {
            log.error("ERROR in processing InternalJobUpdateStatus: " + 
                      "unable to save job to the registry")
            val job = jobs.get(jobId)
            job map {j => j.setStatus(status)} getOrElse {
              log.error("ERROR: HtrcAgent({}) received JobSaveCompleted " + 
                      "for non-existent job\tJOB_ID: {}\tSTATUS: {}",
                      user.name, jobId, status)
            }
            // job.get.setStatus(status)
          }
      }
  }

  /*
  def loadSavedJobs(token: String) {
    if(savedJobsReady == false) {
      val f = RegistryHttpClient.listSavedJobs(token)
      val savedJobsF = f flatMap { names =>
        RegistryHttpClient.downloadSavedJobs(names, token)
      }
      // wait time for the development stack is 5 seconds; wait time set to
      // 10 seconds for the production stack, since the produstion registry
      // extension takes longer for users with several jobs (100+)
      val savedJobsRaw = scala.concurrent.Await.result(savedJobsF, 10 seconds)
      savedJobsRaw.foreach(j => savedJobs += (JobId(j.id) -> j))
      savedJobsReady = true
    }
  }
   */

  // statuses of jobs that are not saved are stored in "jobs" in the HtrcJob
  // object; so, "jobs" includes active jobs (status Created, Staging,
  // Queued, or Running) and completed jobs (status Finished, Crashed, or
  // TimedOut
  def bulkJobStatus(sender: ActorRef, saved: Option[List[SavedHtrcJob]] = None) {
    val jobStatusMsg = 
      <jobs>
        {for( j <- jobs.values.toList ) yield j.status.renderXml}
        {for( j <- saved.getOrElse(Nil) ) yield j.renderXml}
      </jobs>
    sender ! jobStatusMsg
  }

  // statuses of active jobs in the "jobs" list; the "jobs" list contains all
  // jobs that have not been saved to the registry
  def activeJobStatus(sender: ActorRef) {
    val jobStatusMsg = 
      <jobs>
        {for( j <- jobs.values.toList if (!(j.status.isInstanceOf[JobComplete]))) 
           yield j.status.renderXml}
      </jobs>
    sender ! jobStatusMsg
  }

  // save completed jobs to the registry and notify HtrcAgent when the save
  // op has been completed
  def handleCompletedJobs(jobId: JobId, status: JobComplete, token: String) = {
    // save to the registry using the token received in the
    // InternalUpdateJobStatus msg which in turn is the token received in the
    // "updatestatus" msg from the AgentJobClient
    val f1 = RegistryHttpClient.saveJob(status, jobId.toString, token)
    f1 map { firstSaveRes =>
      if (firstSaveRes) 
        self ! JobSaveCompleted(jobId, status, true)
      else {
        // if the save using the given token is unsuccessful, obtain a new
        // client credentials type token and save using this token
        val ccTokenFuture = IdentityServerClient.getClientCredentialsToken
        ccTokenFuture map { clientCredentialsToken =>
          if (clientCredentialsToken != null) {
            val f2 = RegistryHttpClient.saveJob(status, jobId.toString,
                                                clientCredentialsToken) 
            f2 map { secondSaveRes =>
              self ! JobSaveCompleted(jobId, status, secondSaveRes)
            }
          }
          else self ! JobSaveCompleted(jobId, status, false)
        }
      }
    }
  }

  // old version of bulkJobStatus: sends msgs to LocalMachineJob actors for
  // active jobs to obtain their statuses
  def oldBulkJobStatus(sender: ActorRef, 
                        saved: Option[List[SavedHtrcJob]] = None) {
    val futures =
      (for( (id,job) <- jobs ) yield {
        job.ref flatMap { j =>
          j ? JobStatusRequest(id)
        }        
      }).toList
    Future.sequence(futures).mapTo[List[NodeSeq]].map { l =>
      if(saved == None) {
        <jobs>
          {for( j <- l ) yield j}
        </jobs>
      } else {
        <jobs>
          {for( j <- l ) yield j}
          {for( j <- saved.get ) yield j.renderXml}
        </jobs>
      }
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
