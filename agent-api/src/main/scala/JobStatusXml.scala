
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

// The various job status responses must be converted to XML before
// being sent back to the requester.

// The implementation strategy is to define a JobStatus trait with a
// toXml method, and then define a set of classes that provide the
// missing information.

// Currently a bunch of info is inserted by just including a pointer
// to the JobInputs for this job. Should this be changed?

import java.util.Date
import java.text.SimpleDateFormat
import scala.xml._

trait JobStatus {

  val inputs: JobInputs

  val id: JobId
  val computeResource: String
  val submitter = inputs.user.submitter
  val name = inputs.name
  val user = inputs.user
  val algorithm = inputs.algorithm
  val parameters = inputs.rawParameters
  val status: Elem
  val date = 
    (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new java.util.Date)

  // switch to "saved" when job is saved
  var saved = "unsaved"

  def renderXml: NodeSeq =
    <job_status>
      <job_name>{name}</job_name>
      <user>{submitter}</user>
      <algorithm>{algorithm}</algorithm>
      {parameters}
      <job_id>{id}</job_id>
      <date>{date}</date>
      <saved>{saved}</saved>
      {status}
    </job_status>

  override def toString: String = 
    "JobStatus(" + (status \ "@type" text) + ")"
}

// trait representing the state of a job that is done except for end-of-job
// actions such as copying results, stderr, stdout files to the required
// location; PendingCompletion values are not supposed to be (a) recorded
// anywhere as the job status (e.g., in the jobs HashMap in HtrcAgent), or
// (b) sent to the portal; PendingCompletion job statuses are merely for
// internal use by HtrcAgent, which needs to perform end-of-job actions
// before it can update the status of the job to Finished or Crashed
sealed trait PendingCompletion extends JobStatus {
  val jobRuntime: String
}

// trait representing the state of a job that is completely done
trait JobComplete extends JobStatus {
  val results: List[JobResult]

  def saveXml: NodeSeq = {
    <job_status>
      <job_name>{name}</job_name>
      <user>{submitter}</user>
      <algorithm>{algorithm}</algorithm>
      {parameters}
      <job_id>{id}</job_id>
      <date>{date}</date>
      <saved>saved</saved>
      <status type={status \ "@type" text}>
        <results>
          {for(r <- results) yield r.saveXml}
        </results>
      </status>
    </job_status>
  } 
}

// following was named Queued when there was no QueuedOnTarget class; this is
// the status at the moment when the job's LocalMachineJob instance is
// created
case class Queued(inputs: JobInputs, id: JobId) extends JobStatus {
  val computeResource = ""
  val status = <status type="Created"/>
}

case class Staging(inputs: JobInputs, id: JobId, computeResource: String) extends JobStatus {
  val status = <status type="Staging"/>
}

// QueuedOnTarget ("queued on target machine on which jobs are run") refers
// to the state when the job has been placed on the cluster's job queue,
// e.g., on Quarry; so named because class Queued already exists
case class QueuedOnTarget(inputs: JobInputs, id: JobId, computeResource: String) extends JobStatus {
  val status = <status type="Queued"/>
}

case class Running(inputs: JobInputs, id: JobId, computeResource: String) extends JobStatus {
  val status = <status type="Running"/>
}

// job status is XPendingCompletion if the job process itself has finished,
// but end-of-job tasks such as copying job results, creating standard output
// and error files etc. are pending; see trait PendingCompletion
case class FinishedPendingCompletion(
  inputs: JobInputs, id: JobId, computeResource: String, 
  jobRuntime: String) extends PendingCompletion {
  val status = <status type="FinishedPendingCompletion"/>
}

case class CrashedPendingCompletion(
  inputs: JobInputs, id: JobId, computeResource: String, 
  copyResults: Boolean, jobRuntime: String) extends PendingCompletion {
  val status = <status type="CrashedPendingCompletion"/>
}

case class CrashedWithErrorPendingCompletion(
  inputs: JobInputs, id: JobId, computeResource: String, 
  stderr: String, stdout: String) extends PendingCompletion {
  val status = <status type="CrashedWithErrorPendingCompletion"/>
  val jobRuntime = "0"
}

case class TimedOutPendingCompletion(
  inputs: JobInputs, id: JobId, computeResource: String, 
  jobRuntime: String) extends PendingCompletion {
  val status = <status type="TimedOutPendingCompletion"/>
}

// case class Finished(inputs: JobInputs, id: JobId, computeResource: String, 
//                     results: List[JobResult]) extends JobStatus {
//   val status =
//     <status type="Finished">
//       <results>
//         {for(r <- results) yield r.renderXml}
//       </results>
//     </status>

//   def saveXml: NodeSeq = {
//     <job_status>
//       <job_name>{name}</job_name>
//       <user>{submitter}</user>
//       <algorithm>{algorithm}</algorithm>
//       {parameters}
//       <job_id>{id}</job_id>
//       <date>{date}</date>
//       <saved>saved</saved>
//       <status type="Finished">
//         <results>
//           {for(r <- results) yield r.saveXml}
//         </results>
//       </status>
//     </job_status>
//   } 

// }

// case class TimedOut(inputs: JobInputs, id: JobId, computeResource: String, 
//                     results: List[JobResult]) extends JobStatus {
//   val status =
//     <status type="Timed Out">
//       <results>
//         {for(r <- results) yield r.renderXml}
//       </results>
//     </status>

//   def saveXml: NodeSeq = {
//     <job_status>
//       <job_name>{name}</job_name>
//       <user>{submitter}</user>
//       <algorithm>{algorithm}</algorithm>
//       {parameters}
//       <job_id>{id}</job_id>
//       <date>{date}</date>
//       <saved>saved</saved>
//       <status type="Timed Out">
//         <results>
//           {for(r <- results) yield r.saveXml}
//         </results>
//       </status>
//     </job_status>
//   } 
// }

// case class Crashed(inputs: JobInputs, id: JobId, computeResource: String, 
//                    results: List[JobResult]) extends JobStatus {
//   val status = 
//     <status type="Crashed">
//       <results>
//         {for(r <- results) yield r.renderXml}
//       </results>
//     </status>
// }

case class Finished(inputs: JobInputs, id: JobId, computeResource: String, 
                    results: List[JobResult]) extends JobComplete {
  val status =
    <status type="Finished">
      <results>
        {for(r <- results) yield r.renderXml}
      </results>
    </status>
}

case class TimedOut(inputs: JobInputs, id: JobId, computeResource: String, 
                    results: List[JobResult]) extends JobComplete {
  val status =
    <status type="Timed Out">
      <results>
        {for(r <- results) yield r.renderXml}
      </results>
    </status>
}

case class Crashed(inputs: JobInputs, id: JobId, computeResource: String, 
                   results: List[JobResult]) extends JobComplete {
  val status = 
    <status type="Crashed">
      <results>
        {for(r <- results) yield r.renderXml}
      </results>
    </status>
}

object JobStatus {
  def apply(currStatus: JobStatus, newStatus: NodeSeq): JobStatus = {
    val inputs = currStatus.inputs
    val id = currStatus.id
    val computeResource = currStatus.computeResource

    val newStatusStr = (newStatus \\ "status" \ "@type" text)
    newStatusStr match {
      case "Running" => Running(inputs, id, computeResource)
      case "JobClientError" =>
        val errorMsg = "Error in job client.\n" + (newStatus \ "error" text)
        CrashedWithErrorPendingCompletion(inputs, id, computeResource, 
                                          errorMsg, "")
      case "FinishedPendingCompletion" => 
        val jobRuntime = (newStatus \ "job_runtime" text)
        FinishedPendingCompletion(inputs, id, computeResource, jobRuntime)
      case "CrashedPendingCompletion" => 
        val jobRuntime = (newStatus \ "job_runtime" text)
        // copyResults is set to true assuming that this method is called for
        // jobs that have been successfully launched by AgentJobClient, but
        // then exited with errors
        CrashedPendingCompletion(inputs, id, computeResource, true, jobRuntime)
      case "TimedOutPendingCompletion" => 
        val jobRuntime = (newStatus \ "job_runtime" text)
        // copyResults is set to true assuming that this method is called for
        // jobs that have been successfully launched by AgentJobClient, but
        // then exited with errors
        TimedOutPendingCompletion(inputs, id, computeResource, jobRuntime)
      case _ => {
        println("JobStatus.apply() ERROR: unexpected status string " + 
                newStatusStr)
        null
      }
    }
  }
}

// ... we don't have all the info necessary in the algorithm when it
// actually does the notifying. So we use a different job ->
// supervisor message and the supervisor builds the real one.

trait InternalJobStatus
case object InternalQueued extends InternalJobStatus
case object InternalStaging extends InternalJobStatus
case object InternalQueuedOnTarget extends InternalJobStatus
case object InternalRunning extends InternalJobStatus
case object InternalFinished extends InternalJobStatus
case class InternalCrashed(copyResults: Boolean) extends InternalJobStatus
case class InternalCrashedWithError(stderr: String, stdout: String) extends InternalJobStatus

// We also need a way to render the XML for results.

trait JobResult {

  // for portability we want to load this root value at *render* time
  // specifying it as a def that is called by the renderXml function
  // should do the trick
  def root = HtrcConfig.rootResultUrl
  def name = url.split('/').last
  def renderXml = <result type={name}>{root+url}</result>
  def saveXml = <result type={name}>{url}</result>

  val url: String

}

case class Stdout(url: String) extends JobResult
case class Stderr(url: String) extends JobResult
case class DirectoryResult(url: String) extends JobResult

// When saving results we want to fetch them from the registry and
// parse them back into JobResults

object ResultParser {
    
  // saved results don't have the root part of the url
  def toResult(e: Node): JobResult = {
    val name = (e attribute("type")).get.text
    val url = (e text)
    name match {
      case "stdout.txt" => Stdout(url)
      case "stderr.txt" => Stderr(url)
      case str => DirectoryResult(url)
    }
  }

}

// We store saved jobs as a SavedJobs object

case class SavedHtrcJob(e: NodeSeq) {
  
  def id = (e \ "job_id").text
  
  val name = e \ "job_name"
  val user = e \ "user"
  val algorithm = e \ "algorithm"
  val parameters = e \ "parameters"
  val date = e \ "date"
  val statusType = (e \ "status" \ "@type" text)
  val xmlResults = e \ "status" \ "results" \ "result"
  val results = xmlResults map { r => ResultParser.toResult(r) }

  // auxiliary constructor to construct an instance from a JobComplete object
  def this(jobStatus: JobComplete) = this(jobStatus.saveXml)

  def renderXml: NodeSeq = {
    <job_status>
      {name}
      {user}
      {algorithm}
      {parameters}
      <job_id>{id}</job_id>
      {date}
      <saved>saved</saved>
      <status type={statusType}>
        <results>
          {for(r <- results) yield r.renderXml}
        </results>
      </status>
    </job_status>
  }

}
