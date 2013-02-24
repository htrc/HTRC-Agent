
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
  val name = inputs.name
  val user = "default_user"
  val algorithm = inputs.algorithm
  val parameters = inputs.rawParameters
  val status: Elem
  val date = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new java.util.Date)

  def renderXml: NodeSeq =
    <job_status>
      <job_name>{name}</job_name>
      <user>{user}</user>
      <algorithm>{algorithm}</algorithm>
      {parameters}
      <job_id>{id}</job_id>
      <date>{date}</date>
      {status}
    </job_status>

}

case class Queued(inputs: JobInputs, id: JobId) extends JobStatus {
  val status = <status type="Queued"/>
}

case class Staging(inputs: JobInputs, id: JobId) extends JobStatus {
  val status = <status type="Staging"/>
}

case class Running(inputs: JobInputs, id: JobId) extends JobStatus {
  val status = <status type="Running"/>
}

case class Finished(inputs: JobInputs, id: JobId, results: List[JobResult]) extends JobStatus {
  val status =
    <status type="Finished">
      <results>
        {for(r <- results) yield r.renderXml}
      </results>
    </status>
}

case class Crashed(inputs: JobInputs, id: JobId, results: List[JobResult]) extends JobStatus {
  val status = 
    <status type="Crashed">
      <results>
        {for(r <- results) yield r.renderXml}
      </results>
    </status>
}

// ... we don't have all the info necessary in the algorithm when it
// actually does the notifying. So we use a different job ->
// supervisor message and the supervisor builds the real one.

trait InternalJobStatus
case object InternalQueued extends InternalJobStatus
case object InternalStaging extends InternalJobStatus
case object InternalRunning extends InternalJobStatus
case object InternalFinished extends InternalJobStatus
case object InternalCrashed extends InternalJobStatus

// We also need a way to render the XML for results.

trait JobResult {

  val root = HtrcConfig.rootResultUrl
  def name = url.split('/').last
  def renderXml = <result type={name}>{root+url}</result>

  val url: String

}

case class Stdout(url: String) extends JobResult
case class Stderr(url: String) extends JobResult
case class DirectoryResult(url: String) extends JobResult






