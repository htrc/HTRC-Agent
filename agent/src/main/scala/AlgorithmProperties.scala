
// figure out all this property union nonsense here

package htrcagent

import scala.collection.mutable.HashMap
import scala.xml._

import java.io.File
import java.io.PrintWriter

case class AlgorithmProperties(userProperties: NodeSeq, 
                               masterProperties: NodeSeq,
                               algorithmName: String,
                               algId: String,
                               username: String,
                               token: String) {

  // what format are the masterProperties in?
  // step 1 is probably to convert both into maps
  // step 2 is to figure out what the "always" properties are

  val userProps = new HashMap[String,String]
  userProperties \ "e" foreach { e => userProps += ((e \ "@key" text) -> e.text) }

  val masterProps = new HashMap[String,String]
  (masterProperties \ "system_properties") \ "e" foreach { e => 
    masterProps += (( e \ "@key" text) -> e.text)
  }

  // now check for values flagged as requiring inserts:
  masterProps foreach { 
    case (k,"SYS_INSERT") => 
      val input = k match {
        case "token" => token
        case "data_api" => "https://localhost:25443"
        case _ => "ERROR_BAD_SYS_INSERT_VALUE"
      }
      masterProps(k) = input
  }

  // a way to store what goes from the registry to the working dir
  // will be a map of registry path -> dest path
  val registryData = new HashMap[String,String]
  val dependencies = masterProperties \ "dependencies"
  dependencies \ "e" foreach { e => registryData += ((e \ "@key" text) -> e.text) }

  // everything needs a run script right?
  // assumes this is loaded to file using registryData first
  val runScript = masterProperties \ "run_script" text
  val propFileName = masterProperties \ "properties_file_name" text

  // write out a properties file for the algorithm
  def write(workingDir: String) {
    
    def printToFile(f: File)(op: PrintWriter => Unit) {
      val p = new PrintWriter(f)
      try { op(p) } finally { p.close() }
    }

    val allProps = userProps ++ masterProps
    if(allProps.isEmpty == false) {
      printToFile(new File(workingDir+"/"+propFileName))(p => {
        allProps.foreach { case (k,v) => p.println(k + "=" + v) }
      })
    }

  }

}
