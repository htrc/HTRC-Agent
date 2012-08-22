
// the states an algorithm can be in
// todo : change the names on these to match actual desired statuses

package htrcagent

import java.util.Date
import scala.xml._

trait AlgorithmStatus {
  val date: Date
  val algId: String
  val status: String
  def renderXml: NodeSeq = 
    <algStatus>
      <date>{date}</date>
      <algId>{algId}</algId>
      <status>{status}</status>
    </algStatus>
}

case class Prestart(date: Date, algId: String) extends AlgorithmStatus {
  val status = "Prestart"
}

case class Initializing(date: Date, algId: String) extends AlgorithmStatus {
  val status = "Initializing"
}

case class Running(date: Date, algId: String) extends AlgorithmStatus {
  val status = "Running"
}

case class Finished(date: Date, algId: String) extends AlgorithmStatus { 
  val status = "Finished"
}

case class Crashed(date: Date, algId: String) extends AlgorithmStatus {
  val status = "Crashed"
}


trait AlgorithmResult {
  def renderXml: Elem
}

case class StdoutResult(stdout: String) extends AlgorithmResult {
  def renderXml: Elem = <stdout>{stdout}</stdout>
}

case class StderrResult(stderr: String) extends AlgorithmResult {
  def renderXml: Elem = <stderr>{stderr}</stderr>
}

case object EmptyResult extends AlgorithmResult {
  def renderXml: Elem = <empty>result does not exist</empty>
}
