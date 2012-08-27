
// the states an algorithm can be in
// todo : change the names on these to match actual desired statuses

package htrcagent

import java.util.Date
import scala.xml._

trait AlgorithmStatus {
  val date: Date
  val jobId: String
  val jobName: String
  val user: String
  val algName: String
  val status: Elem
  def renderXml: NodeSeq = 
    <job_status>
      <job_name>{jobName}</job_name>
      <user>{user}</user>
      <algorithm>{algName}</algorithm>
      <job_id>{jobId}</job_id>
      <date>{date}</date>
      {status}
    </job_status>
}

case class Prestart(date: Date, jobId: String, jobName: String, user: String, algName: String) extends AlgorithmStatus {
  val status = <status>Prestart</status>
}

case class Initializing(date: Date, jobId: String, jobName: String, user: String, algName: String) extends AlgorithmStatus {
  val status = <status>Initializing</status>
}

case class Running(date: Date, jobId: String, jobName: String, user: String, algName: String) extends AlgorithmStatus {
  val status = <status>Running</status>
}

case class Finished(date: Date, jobId: String, jobName: String, user: String, algName: String) extends AlgorithmStatus { 
  val status = <status>Finished</status>
}

case class Crashed(date: Date, jobId: String, jobName: String, user: String, algName: String) extends AlgorithmStatus {
  val status = <status>Crashed</status>
}

trait AlgorithmResult {
  def renderXml: Elem
  val rtype: String
}

case class StdoutResult(stdout: String) extends AlgorithmResult {
  val rtype = "stdout"
  def renderXml: Elem = <stdout>{stdout}</stdout>
}

case class StderrResult(stderr: String) extends AlgorithmResult {
  val rtype = "stderr"
  def renderXml: Elem = <stderr>{stderr}</stderr>
}

case class DirResult(path: String) extends AlgorithmResult {
  val rtype = "dir"
  def renderXml: Elem = <dir_path>{path}</dir_path>
}

case object EmptyResult extends AlgorithmResult {
  val rtype = "empty"
  def renderXml: Elem = <empty>result does not exist</empty>
}
