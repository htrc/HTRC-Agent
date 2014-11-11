
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

// JobCompletionTask: performs end-of-job actions such as copying job results
// stderr and stdout files to results location

import akka.actor.{ Actor, Props, ActorRef, ActorContext, PoisonPill }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import akka.event.slf4j.Logger
import java.io.File
import java.io.PrintWriter
import akka.pattern.ask
import scala.concurrent._

abstract class JobCompletionTask extends Actor {
  import HtrcUtils._

  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)
  val auditLog = Logger("audit")

  val status: PendingCompletion
  val token: String

  val inputs = status.inputs
  val id = status.id
  val userName = status.submitter
  val computeResource = status.computeResource

  val userActor = parent // ActorRef to the HtrcAgent actor that represents
			 // the user associated with the job being completed

  val outputDir = HtrcConfig.systemVariables("output_dir")
  val localResultDir = HtrcConfig.resultDir + "/" + userName + "/" + id

  var copyResults: Boolean = false
  var crashed: Boolean = false
  var timedOut: Boolean = false
  status match {
    case CrashedWithErrorPendingCompletion(_, _, _, stderr, stdout) =>
      HtrcUtils.writeFile(stderr, "stderr.txt", userName, id)
      HtrcUtils.writeFile(stdout, "stdout.txt", userName, id)
      crashed = true
    case CrashedPendingCompletion(_, _, _, copyRes, _) =>
      copyResults = copyRes
      crashed = true
    case FinishedPendingCompletion(_, _, _, _) => 
      copyResults = true
    case TimedOutPendingCompletion(_, _, _, _) => 
      timedOut = true
  }

  // copy job results, job and job client error and output files if necessary
  var scpRes = 0
  if (copyResults)
    scpRes = copyJobResultsFromCompRes()
  else if (timedOut) {
    scpRes = copyJobResultsFromCompResOnTimeOut()
    val timeoutErrorMsg = 
      "Job status: Timed Out. Allotted time limit exceeded. Job killed.\n" 
    val pathPrefix = localResultDir + "/"
    HtrcUtils.appendToStdOutErrFile(timeoutErrorMsg, pathPrefix + "stderr.txt")
  }

  // create list of job result, output, and error files that becomes part of
  // the final job status
  val results = processJobResults((copyResults || timedOut))

  // determine the final status of the job
  var finalStatus: JobStatus = null
  if (crashed) 
    finalStatus = Crashed(inputs, id, computeResource, results)
  else if (timedOut)
    finalStatus = TimedOut(inputs, id, computeResource, results)
  else if (scpRes != 0) {
    finalStatus = Crashed(inputs, id, computeResource, results)
    crashed = true
  }
  else finalStatus = Finished(inputs, id, computeResource, results)
 
  // for jobs running on local shell
  JobThrottler.removeJob()

  // write to audit log
  val jobRuntime = status.jobRuntime
  logEnd(if (crashed || timedOut) "CRASHED" else "FINISHED", jobRuntime)

  // send the final job status to the user actor
  userActor ! InternalUpdateJobStatus(id, finalStatus, token)

  // need to self-destruct after performing necessary tasks and sending msg
  // with finalStatus to userActor; ensure that this is correct; instead of
  // this actor killing itself, it may be better for the recipient of
  // InternalUpdateJobStatus or HtrcAgent parent of this actor to kill this
  // actor
  //self ! PoisonPill
  
  // copyJobResultsFromCompRes: abstract method to copy job results, job
  // stderr, stdout files, job client error and output files from the compute
  // resource on which the job ran
  def copyJobResultsFromCompRes(): Int

  // copyJobResultsFromCompResOnTimeOut: abstract method to copy job results,
  // job stderr, stdout files from the compute resource on which the job ran
  def copyJobResultsFromCompResOnTimeOut(): Int

  def processJobResults(anyJobResults: Boolean) = {
    var results: List[JobResult] = Nil
    if (anyJobResults) {
      // go through expected job result filenames, and check if they exist in
      // the local result dir; jobs may return exit val 0 even when the job
      // was unsuccessful and did not produce result files; or the copy from
      // the compute resource may have failed to copy some/all job result
      // files
      val validResults = 
	inputs.resultNames filter {x => fileExists(localJobResultFile(x))}

      val dirResults = validResults map { n => 
	DirectoryResult(userName+"/"+id+"/"+outputDir+"/"+n) }
      log.debug("TASK_RESULTS\t{}\t{}\tJOB_ID: {}\tRAW: {}",
		userName, inputs.ip, id, dirResults)

      // add valid results to list-of-results 
      dirResults foreach { r => results = r :: results }
    }

    val stdoutUrl = (userName + "/" + id + "/" + "stdout.txt")
    val stderrUrl = (userName + "/" + id + "/" + "stderr.txt")
    Stdout(stdoutUrl) :: Stderr(stderrUrl) :: results
  }

  // log job termination in the audit log file; t is the final status of the
  // job, totalTime is the job runtime in seconds
  def logEnd(t: String, totalTime: String) {
    // for audit log analyzer
    // type end_status request_id user ip token job_id job_name algorithm run_time
    val fstr = "JOB_TERMINATION\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s".format(t, 
               inputs.requestId, userName, inputs.ip, inputs.token, id,
               inputs.name, inputs.algorithm, totalTime)
    auditLog.info(fstr)
  }

  def localJobResultFile(file: String): String =
    localResultDir + "/" + outputDir + "/" + file

  // this actor performs a set of tasks but does not receive any messages
  def receive = {
    case m =>
      log.error("JobCompletionTask: unexpected message " + m)
  }
}

// factory object for creating the appropriate JobCompletionTasks
object JobCompletionTask {
  def apply(status: PendingCompletion, token: String, context: ActorContext) = {
    if (status.computeResource != "shell") 
      context.actorOf(Props(new RemoteJobCompletion(status, token)))
    else null
  }
}
