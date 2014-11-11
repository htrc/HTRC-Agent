
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

// An actor that supervises and runs the local machine job type.

import akka.actor.{ Actor, Props, ActorRef, PoisonPill }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import akka.event.slf4j.Logger
import java.io._

class LocalMachineJob(user: HtrcUser, inputs: JobInputs, id: JobId) extends Actor {

  import HtrcUtils._

  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)
  val auditLog = Logger("audit")

  // log.debug("LOCAL_MACHINE_JOB_ACTOR_STARTED\t{}\t{}\tJOB_ID: {}",
  //         user.name, "ip", id)

  var userActor: ActorRef = null

  // The mutable state representing current status.
  var status: JobStatus = Queued(inputs, id)
  // the compute resource on which the job is run
  val computeResource = HtrcConfig.localResourceType

  // As a local machine shell job, we just start our child directly.
  var child: ActorRef = null
  // our makeChild should check the config file to see if we are using
  // PBS, SLURM, or shell tasks
  def makeChild = {
    if (HtrcConfig.jobScheduler == "PBS") {
      actorOf(Props(new PBSTask(user, inputs, id)))  
    } else if (HtrcConfig.jobScheduler == "SLURM") {
      actorOf(Props(new SLURMTask(user, inputs, id)))  
    } else {
      actorOf(Props(new ShellTask(user, inputs, id)))
    }
  }

  // mutable state party time
  // var results: List[JobResult] = Nil
  // var stdoutResult: Stdout = null
  // var stderrResult: Stderr = null

  // how long did the job take?
  // val startTime = java.lang.System.currentTimeMillis
  // var jobRuntime = "0"
  // def totalTime: String = { 
  //   if (HtrcConfig.jobScheduler == "PBS")
  //   jobRuntime
  //   else {
  //     val endTime = java.lang.System.currentTimeMillis
  //     ((endTime - startTime) / 1000).toString
  //   }
  // }

  // def logEnd(t: String) {
  //   // for audit log analyzer
  //   // type end_status request_id user ip token job_id job_name algorithm run_time
  //   val fstr = "JOB_TERMINATION\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s".format(t, 
  //              inputs.requestId, user.name, inputs.ip, inputs.token, id,
  //              inputs.name, inputs.algorithm, totalTime)
  //   auditLog.info(fstr)
  // }
    
  val behavior: PartialFunction[Any,Unit] = {
    case m: JobMessage => {
      m match {
        // case Result(res) =>
        //   results = res :: results
        case SaveJob(id, token) =>
          status match {
            case s @ Finished(_,id,_,_) =>
              RegistryHttpClient.saveJob(s, id.toString, token)
              s.saved = "saved"
              sender ! <job>Saved job</job>
            case s => 
              sender ! <error>Job not yet finished or is crashed. Failed to save.</error>
          }
        case DeleteJob(id, token) =>
          sender ! <success>deleted job: {id}</success>
          self ! PoisonPill
        // case JobStatusRequest(id) =>
        //   log.debug("JOB_ACTOR_STATUS_REQUEST\t{}\t{}\tJOB_ID: {}\tSTATUS: {}",
        //           user.name, "ip", id, status)
        //   sender ! status.renderXml
        case StatusUpdate(newStatus) =>
          log.debug("JOB_ACTOR_STATUS_UPDATE\t{}\t{}\tJOB_ID: {}\tSTATUS: {}",
                   user.name, "ip", id, newStatus)
          newStatus match {
            case InternalQueued =>
              status = Queued(inputs, id)
              userActor ! InternalUpdateJobStatus(id, status, inputs.token)
            case InternalStaging =>
              status = Staging(inputs, id, computeResource)
              userActor ! InternalUpdateJobStatus(id, status, inputs.token)
            case InternalQueuedOnTarget =>
              status = QueuedOnTarget(inputs, id, computeResource)
              userActor ! InternalUpdateJobStatus(id, status, inputs.token)
            case InternalCrashedWithError(stderr, stdout) =>
              status = 
                CrashedWithErrorPendingCompletion(inputs, id, computeResource,
                                                  stderr, stdout)
              userActor ! InternalUpdateJobStatus(id, status, inputs.token)
            case InternalRunning =>
              log.debug("JOB_ACTOR_ERROR: unexpected status update InternalRunning, USER: {}\tJOB_ID: {}", user.name, id)
              // status = Running(inputs, id, computeResource)
              // userActor ! InternalUpdateJobStatus(id, status)
            case InternalFinished =>
              log.debug("JOB_ACTOR_ERROR: unexpected status update InternalFinished, USER: {}\tJOB_ID: {}", user.name, id)
              // JobThrottler.removeJob()
              // logEnd("FINISHED")
              // status = FinishedPendingCompletion(inputs, id, computeResource)
              // userActor ! InternalUpdateJobStatus(id, status)
            case InternalCrashed(copyResults) =>
              log.debug("JOB_ACTOR_ERROR: unexpected status update InternalCrashed, USER: {}\tJOB_ID: {}", user.name, id)
              // JobThrottler.removeJob()
              // logEnd("CRASHED")
              // status = CrashedPendingCompletion(inputs, id, computeResource,
              //                                   copyResults)
              // userActor ! InternalUpdateJobStatus(id, status)
          }
        // case StdoutChunk(str) =>
        //   stdout.append(str + "\n")
        // case StderrChunk(str) =>
        //   stderr.append(str + "\n")
        // case JobRuntime(str) =>
        //   jobRuntime = str
        // case JobOutputRequest(id, "stdout") =>
        //   sender ! stdoutResult.renderXml
        // case JobOutputRequest(id, "stderr") =>
        //   sender ! stderrResult.renderXml
        // case JobOutputRequest(id, outputType) =>
        //   sender ! "unrecognized output type: " + outputType
        case RunJob =>
          log.debug("launching job")
          userActor = sender
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
