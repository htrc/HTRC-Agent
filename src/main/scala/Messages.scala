
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

case class BuildAgent(user: HtrcUser, message: AgentMessage)

sealed trait JobCreatorMessage
sealed trait ComputeResourceMessage
sealed trait JobMessage
sealed trait AgentMessage
sealed trait RegistryMessage
sealed trait WriteStatus

case class CreateJob(user: HtrcUser, inputs: JobInputs, id: JobId) extends JobCreatorMessage with ComputeResourceMessage


case class SaveJob(jobId: JobId, token: String) extends AgentMessage with JobMessage
case class DeleteJob(jobId: JobId, token: String) extends AgentMessage with JobMessage
case class RunAlgorithm(name: String, inputs: JobInputs) extends AgentMessage
case class JobStatusRequest(jobId: JobId) extends AgentMessage with JobMessage
case object ActiveJobStatuses extends AgentMessage
case class AllJobStatuses(token: String) extends AgentMessage
case class SavedJobStatuses(token: String) extends AgentMessage
case class JobOutputRequest(jobId: JobId, outputType: String) extends AgentMessage with JobMessage


case class StatusUpdate(status: InternalJobStatus) extends JobMessage
case class StdoutChunk(str: String) extends JobMessage
case class StderrChunk(str: String) extends JobMessage
case object RunJob extends JobMessage
case class Result(res: JobResult) extends JobMessage

case class WriteFile(path: String, name: String, workingDir: String, token: String) extends RegistryMessage
case class WriteCollection(name: String, workingDir: String, token: String) extends RegistryMessage

case class RegistryError(e: String) extends WriteStatus
case object RegistryOk extends WriteStatus

