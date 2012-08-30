
// the states an algorithm can be in
// todo : change the names on these to match actual desired statuses

package htrcagent

import java.util.Date
import java.text.SimpleDateFormat
import scala.xml._

trait AlgorithmStatus {

  val props: AlgorithmProperties

  val jobId = props.jobId
  val jobName = props.jobName
  val user = props.username
  val algName = props.algorithmName
  val status: Elem
  val parameters = props.rawParameters

  val fDate = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new java.util.Date)

  def renderXml: NodeSeq = 
    <job_status>
      <job_name>{jobName}</job_name>
      <user>{user}</user>
      <algorithm>{algName}</algorithm>
      {parameters}
      <job_id>{jobId}</job_id>
      <date>{fDate}</date>
      {status}
    </job_status>
}

case class Queued(props: AlgorithmProperties) extends AlgorithmStatus {
  val status = <status type="Queued"/>
}

case class Staging(props: AlgorithmProperties) extends AlgorithmStatus {
  val status = <status type="Staging"/>
}

case class Running(props: AlgorithmProperties) extends AlgorithmStatus {
  val status = <status type="Running"/>
}

case class Finished(props: AlgorithmProperties, results: List[AlgorithmResult]) extends AlgorithmStatus { 
  val status = 
    <status type="Finished">
      <results>
        {for(r <- results) yield r.renderXml}
      </results>
    </status>
}

case class Crashed(props: AlgorithmProperties, results: List[AlgorithmResult]) extends AlgorithmStatus {
  val status =
    <status type="Crashed">   
      <message>Job crashed for unknown reason.</message>
      {for(r <- results) yield r.renderXml}
    </status>
}

case class Aborted(props: AlgorithmProperties) extends AlgorithmStatus {
  val status =
    <status type="Aborted">
      <message>User aborted job.</message>
    </status>
}

trait AlgorithmResult {
  val rootUrl = HtrcProps.resultRootUrl
  def renderXml = <result type={rtype}>{rootUrl+"/"+result}</result>
  val result: String
  val rtype: String
}

case class StdoutResult(result: String) extends AlgorithmResult {
  val rtype = "stdout"
}

case class StderrResult(result: String) extends AlgorithmResult {
  val rtype = "stderr"
}

case class DirectoryResult(result: String) extends AlgorithmResult {
  val rtype = "directory"
}

case object EmptyResult extends AlgorithmResult {
  val result = "does_not_exist"
  val rtype = "empty"
}
