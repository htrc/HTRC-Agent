
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

// Messages not specific to the agent, eventually refactor to better locations

import scala.xml._
import akka.actor.ActorRef

case class BuildAgent(user: HtrcUser, message: AgentMessage)

sealed trait JobCreatorMessage
sealed trait ComputeResourceMessage
sealed trait JobMessage
sealed trait AgentMessage
sealed trait RegistryMessage
sealed trait WriteStatus
sealed trait CacheControllerMessage

case class CreateJob(user: HtrcUser, inputs: JobInputs, id: JobId) extends JobCreatorMessage with ComputeResourceMessage
case class CreateCachedJob(inputs: JobInputs, id: JobId, cachedJobId: String) extends JobCreatorMessage

case class SaveJob(jobId: JobId, token: String) extends AgentMessage
case class DeleteJob(jobId: JobId, token: String) extends AgentMessage with JobMessage
case class RunAlgorithm(inputs: JobInputs) extends AgentMessage
case class CreateJobFromCache(inputs: JobInputs, cachedJobId: String) extends AgentMessage
case class JobStatusRequest(jobId: JobId) extends AgentMessage
case object ActiveJobStatuses extends AgentMessage
case class AllJobStatuses(token: String) extends AgentMessage
case class SavedJobStatuses(token: String) extends AgentMessage
// case class JobOutputRequest(jobId: JobId, outputType: String) extends AgentMessage with JobMessage

// msg sent to HtrcAgent upon receipt of job/<jobid>/updatestatus from
// AgentJobClient
case class UpdateJobStatus(jobId: JobId, token: String, 
                           content: NodeSeq) extends AgentMessage {
  // val statusStr = content \\ "status" \ "@type" text
}

// InternalUpdateJobStatus contains a JobId and the new status of the job; it
// is received by HtrcAgent from itself or other actors such as
// LocalMachineJob and JobCompletionTask
case class InternalUpdateJobStatus(jobId: JobId, status: JobStatus, 
                                   token: String) extends AgentMessage

// JobSaveCompleted is sent by HtrcAgent to itself once the registry call to
// save a job with status JobComplete has been completed
case class JobSaveCompleted(jobId: JobId, status: JobComplete, 
                            saveResult: Boolean) extends AgentMessage

// StatusUpdate is sent from PBSTask, SLURMTask, Shelltask to LocalMachineJob
case class StatusUpdate(status: InternalJobStatus) extends JobMessage
case object RunJob extends JobMessage

// case class StdoutChunk(str: String) extends JobMessage
// case class StderrChunk(str: String) extends JobMessage
// case class JobRuntime(str: String) extends JobMessage
// case class Result(res: JobResult) extends JobMessage

case class WriteFile(path: String, name: String, workingDir: String, inputs: JobInputs) extends RegistryMessage
case class WriteCollection(name: String, workingDir: String, inputs: JobInputs) extends RegistryMessage

case class RegistryError(e: String) extends WriteStatus
case object RegistryOk extends WriteStatus

// msgs sent from AgentServiceActor to CacheController 
case class GetJobFromCache(js: JobSubmission, token: String) extends CacheControllerMessage
case class GetDataForJobRun(js: JobSubmission, token: String) extends CacheControllerMessage

// msg sent by CacheController to itself after constructing the lookup key upon
// receiving GetJobFromCache
case class CacheLookup(optKey: Option[String], algMetadata: JobProperties, 
                       asker: ActorRef) extends CacheControllerMessage

// periodic msg sent by CacheController to itself
case object WriteCacheToFile extends CacheControllerMessage

// msg sent from HtrcAgent to CacheController; only "finished" jobs can be
// added to the cache
case class AddJobToCache(key: String, jobStatus: Finished) extends CacheControllerMessage

