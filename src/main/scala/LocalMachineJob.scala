
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
import java.io._

class LocalMachineJob(user: HtrcUser, inputs: JobInputs, id: JobId) extends Actor {

  import HtrcUtils._

  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  log.debug("LOCAL_MACHINE_JOB_ACTOR_STARTED\t{}\t{}\tJOB_ID: {}",
           user.name, "ip", id)

  // The mutable state representing current status.
  val stdout = new StringBuilder
  val stderr = new StringBuilder
  var status: JobStatus = Queued(inputs, id)

  // As a local machine shell job, we just start our child directly.
  var child: ActorRef = null
  // our makeChild should check the config file to see if we are using
  // odin or shell tasks
  def makeChild = {
    if(HtrcConfig.localResourceType == "odin") {
      actorOf(Props(new OdinTask(user, inputs, id)))  
    } else {
      actorOf(Props(new ShellTask(user, inputs, id)))
    }
  }

  // mutable state party time
  var results: List[JobResult] = Nil
  var stdoutResult: Stdout = null
  var stderrResult: Stderr = null

  // how long did the job take?
  val startTime = java.lang.System.currentTimeMillis
  def totalTime: String = { 
    val endTime = java.lang.System.currentTimeMillis
    ((endTime - startTime) / 60).toString
  }

  def logEnd(t: String) {
    // for audit log analyzer
    // type end_status request_id user ip token job_id job_name algorithm run_time
    val fstr = "JOB_TERMINATION\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s".format(t, 
               inputs.requestId, user.name, inputs.ip, inputs.token, id,
               inputs.name, inputs.algorithm, totalTime)
    log.info(fstr)
  }
    
  val behavior: PartialFunction[Any,Unit] = {
    case m: JobMessage => {
      m match {
        case Result(res) =>
          results = res :: results
        case SaveJob(id, token) =>
          status match {
            case s @ Finished(_,id,_) =>
              RegistryHttpClient.saveJob(s, id.toString, token)
              s.saved = "saved"
              sender ! <job>Saved job</job>
            case s => 
              sender ! <error>Job not yet finished or is crashed. Failed to save.</error>
          }
        case DeleteJob(id, token) =>
          sender ! <success>deleted job: {id}</success>
          self ! PoisonPill
        case JobStatusRequest(id) =>
          log.debug("JOB_ACTOR_STATUS_REQUEST\t{}\t{}\tJOB_ID: {}\tSTATUS: {}",
                   user.name, "ip", id, status)
          sender ! status.renderXml
        case StatusUpdate(newStatus) =>
          log.debug("JOB_ACTOR_STATUS_UPDATE\t{}\t{}\tJOB_ID: {}\tSTATUS: {}",
                   user.name, "ip", id, newStatus)
          newStatus match {
            case InternalQueued =>
              status = Queued(inputs, id)
            case InternalStaging =>
              status = Staging(inputs, id)
            case InternalRunning =>
              status = Running(inputs, id)
            case InternalFinished =>
              JobThrottler.removeJob()
              logEnd("FINISHED")
              val stdoutUrl = writeFile(stdout.toString, "stdout.txt", user, id)
              val stderrUrl = writeFile(stderr.toString, "stderr.txt", user, id)
              stdoutResult = Stdout(stdoutUrl)
              stderrResult = Stderr(stderrUrl)
              results = stdoutResult :: stderrResult :: results
              status = Finished(inputs, id, results)
            case InternalCrashed =>
              JobThrottler.removeJob()
              logEnd("CRASHED")
              val stdoutUrl = writeFile(stdout.toString, "stdout.txt", user, id)
              val stderrUrl = writeFile(stderr.toString, "stderr.txt", user, id)
              stdoutResult = Stdout(stdoutUrl)
              stderrResult = Stderr(stderrUrl)
              results = stdoutResult :: stderrResult :: results
              status = Crashed(inputs, id, results)
          }
        case StdoutChunk(str) =>
          stdout.append(str + "\n")
        case StderrChunk(str) =>
          stderr.append(str + "\n")
        case JobOutputRequest(id, "stdout") =>
          sender ! stdoutResult.renderXml
        case JobOutputRequest(id, "stderr") =>
          sender ! stderrResult.renderXml
        case JobOutputRequest(id, outputType) =>
          sender ! "unrecognized output type: " + outputType
        case RunJob =>
          log.debug("launching job")
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
