
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


case class SaveJob(jobId: JobId) extends AgentMessage with JobMessage
case class DeleteJob(jobId: JobId) extends AgentMessage with JobMessage
case class RunAlgorithm(name: String, inputs: JobInputs) extends AgentMessage
case class JobStatusRequest(jobId: JobId) extends AgentMessage with JobMessage
case object ActiveJobStatuses extends AgentMessage
case object AllJobStatuses extends AgentMessage
case object SavedJobStatuses extends AgentMessage
case class JobOutputRequest(jobId: JobId, outputType: String) extends AgentMessage with JobMessage


case class StatusUpdate(status: InternalJobStatus) extends JobMessage
case class StdoutChunk(str: String) extends JobMessage
case class StderrChunk(str: String) extends JobMessage
case object RunJob extends JobMessage

case class WriteFile(path: String, name: String, workingDir: String) extends RegistryMessage
case class WriteCollection(name: String, workingDir: String) extends RegistryMessage

case class RegistryError(e: String) extends WriteStatus
case object RegistryOk extends WriteStatus

