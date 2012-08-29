
// figure out all this property union nonsense here

package htrcagent

import scala.collection.mutable.HashMap
import scala.xml._
import scala.util.matching.Regex._

import java.io.File
import java.io.PrintWriter

case class AlgorithmProperties(userProperties: NodeSeq, 
                               masterProperties: NodeSeq,
                               algorithmName: String,
                               jobId: String,
                               username: String,
                               token: String) {

  // what format are the masterProperties in?
  // A: need to manage params, dependencies blocks and fill in agent info
  // step 1 is probably to convert both into maps
  // step 2 is to figure out what the "always" properties are

  // what do I want from a user!
  // * block with each param

  val exampleUserBlock =
    <job>
      <name>my_job</name>
      <username>drhtrc</username>
      <algorithm>Marc_Downloader</algorithm>
      <parameters>
        <param
          name="foo"
          type="collection"
          value="bar"/>
      </parameters>
    </job>


  val jobName = userProperties \ "name" text

  val rawParameters = userProperties \ "parameters"

  val parameters = new HashMap[String,String]
  userProperties \ "parameters" \ "param" foreach { e => parameters += ((e \ "@name" text) -> (e \ "@value" text)) }

  val masterProps = new HashMap[String,String]
  (masterProperties \ "system_properties") \ "e" foreach { e => 
    masterProps += (( e \ "@key" text) -> e.text)
  }

  // todo : move this variable binding to a config file!
  val systemVariables = new HashMap[String,String]
  systemVariables += ("data_api_url" -> HtrcProps.dataApi)
  systemVariables += ("auth_token" -> token)
  systemVariables += ("solr_proxy" -> HtrcProps.solr)

  // this is temporary - in the future put results someplace sensible
  systemVariables += ("output_dir" -> "job_results")
  // also make available as a prop to the running algorithm - assume all algs use it...
  val outputDir = "job_results"

  val variables = parameters ++ systemVariables

  // now check for variables in the properties
  // the way this currently works is that the user parameters input is used
  // as a way to find variables
  // there are also variables bound by the htrc system

  // this should bind all the variables in system properties to the correct variables

  val aVar1 = """\$\{(\w+)\}""".r
  val aVar2 = """\$(\w+)""".r
  def bindVariables(exp: String): String = {
    val r1 = aVar1 replaceAllIn (exp, (m: Match) => variables.get(m.group(1)).getOrElse("None"))
    val r2 = aVar2 replaceAllIn (r1, (m: Match) => variables.get(m.group(1)).getOrElse("None"))
    r2
  }

  masterProps foreach {
    case (k,v) =>
      masterProps(k) = bindVariables(v)
  }

  // POST CONDITION: there are no variables in master props, only correct values

  // a way to store what goes from the registry to the working dir
  // will be a map of registry path -> dest path
  val registryData = new HashMap[String,String]
  val dependencies = masterProperties \ "dependencies"
  dependencies \ "dependency" foreach { e => registryData += ((e \ "@path" text) -> (e \ "@name" text)) }

  // collections stored here, actual job should fetch from registry the files
  val collections =
    userProperties \ "parameters" \ "param" filter { e => (e \ "@type" text) == "collection" } map { e => (e \ "@value" text) } toList
    
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

    val allProps = masterProps
    if(allProps.isEmpty == false) {
      printToFile(new File(workingDir+"/"+propFileName))(p => {
        allProps.foreach { case (k,v) => p.println(k + " = " + v) }
      })
    }

  }

}
