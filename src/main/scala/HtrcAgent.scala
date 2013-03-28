
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
  var savedJobsReady = false

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

        case SaveJob(jobId, token) => 
          val job = jobs.get(jobId)
          if ( job == None) {
            sender ! <error>job: {jobId} does not exist</error>
          } else {
            job.get dispatch(m) pipeTo sender
          }

        case DeleteJob(jobId, token) => 
          val job = jobs.get(jobId)
          val savedJob = savedJobs.get(jobId)
          if ( job == None && savedJob == None ) {
            sender ! <error>job: {jobId} does not exist</error>
          } else {
            if( savedJob != None ) {
              RegistryHttpClient.deleteJob(jobId.toString, token)
              savedJobs -= jobId
            }
            if( job != None ) {
              jobs -= jobId
              job.get dispatch(m) pipeTo sender
            } else {
              sender ! <success>deleted job: {jobId}</success>
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
            log.info(fstr.format(inputs.requestId, user.name, inputs.ip, 
                                 inputs.token, id.toString,
                                 inputs.name, inputs.algorithm))
            
            // get our job
            val job = 
              (HtrcSystem.jobCreator ? 
               CreateJob(user, inputs, id)).mapTo[ActorRef]
            // somehow we already have a JobId...
            jobs += (id -> HtrcJob(job))
            sender ! Queued(inputs, id).renderXml
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
            (job.get dispatch m) pipeTo sender
          }
            
        case ActiveJobStatuses => 
          bulkJobStatus(sender)

        case SavedJobStatuses(token) => 
          loadSavedJobs(token)
          sender ! <jobs>{for(j <- savedJobs.values) yield j.renderXml}</jobs>

        case AllJobStatuses(token) => 
          loadSavedJobs(token)
          val saved = Some(savedJobs.values.toList)
          bulkJobStatus(sender, saved)

        case JobOutputRequest(jobId, outputType) => 
          val job = jobs.get(jobId)
          if (job == None)
            sender ! <elem>dne</elem>
          else
            (job.get dispatch m) pipeTo sender
      }
  }

  def loadSavedJobs(token: String) {
    if(savedJobsReady == false) {
      val f = RegistryHttpClient.listSavedJobs(token)
      val savedJobsF = f flatMap { names =>
        RegistryHttpClient.downloadSavedJobs(names, token)
      }
      val savedJobsRaw = scala.concurrent.Await.result(savedJobsF, 5 seconds)
      savedJobsRaw.foreach(j => savedJobs += (JobId(j.id) -> j))
      savedJobsReady = true
    }
  }

  def bulkJobStatus(sender: ActorRef, saved: Option[List[SavedHtrcJob]] = None) {
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
