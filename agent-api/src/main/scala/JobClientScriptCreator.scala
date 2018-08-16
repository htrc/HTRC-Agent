
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

// A class that reads in a skeleton job client script and has methods to
// create job-specific scripts used to launch job clients on compute
// resources. The skeleton script is re-read only if modified.

import akka.event.LoggingAdapter
import scala.io.Source
import java.io.File
import java.io.PrintWriter

class JobClientScriptCreator(val skelJobClientScript: String) {
  // val lineSep = sys.props("line.separator")
  val lineSep = "\n"
  val shebang = "#!"

  val scriptFile: File = new File(skelJobClientScript)
  // var lastModified: Long = scriptFile.lastModified
  var lastModified: Long = 0
  var header: StringBuilder = null // contents upto and including "#!" line
  var contents: StringBuilder = null // remaining contents of scriptFile

  // readFile()

  def readFileAndProcess(f: File)(op: Source => Unit) = {
    val src = Source.fromFile(f)
    try { op(src) } finally { src.close() }
  }

  def printToFile(f: File)(op: PrintWriter => Unit) = {
    val writer = new PrintWriter(f)
    try { op(writer) } finally { writer.close() }
  }

  // obtain lines from scriptFile, and set header and contents
  def readFile(): Unit = {
    readFileAndProcess(scriptFile) { src =>
      val sbHeader = new StringBuilder
      val sbContents = new StringBuilder
      var containsShebang = false
      
      var sb = sbHeader
      for (line <- src.getLines) {
        sb.append(line + lineSep)
        if (line.startsWith(shebang)) {
          containsShebang = true
          sb = sbContents
        }
      }
      if (containsShebang) {
        header = sbHeader
        contents = sbContents
      }
      else {
        header = null
        contents = sbHeader
      }
    }
  }
  
  def print(): Unit = {
    println("Header: ")
    if (header != null) header.lines foreach println
    println("Contents: ")
    contents.lines foreach println
  }

  def envVarsSetter(elems: List[(String, String)]): List[String] = {
    elems map {
      case (varName, value) => "export " + varName + "=" + value + lineSep
    }
  }

  // create script file for a particular job
  def createJobClientScript(envVars: List[(String, String)], 
                            resultFile: String,
                            log: LoggingAdapter): Unit = {
    // if scriptFile has modified since the last time it was read into header
    // and contents, then read the file again
    log.debug("createJobClientScript : skelJobClientScript = " + 
              skelJobClientScript + ", scriptFile.lastModified = " + 
              scriptFile.lastModified + ", lastModified = " + lastModified)

    if (scriptFile.lastModified > lastModified)
      readFile()

    printToFile(new File(resultFile)) { writer =>
      // use writer.print instead of of writer.println to account for Windows
      // machines on which the agent may be tested first
      if (header != null) {
        header.linesWithSeparators foreach writer.print
        writer.print(lineSep)
      }
      envVarsSetter(envVars) foreach writer.print
      contents.linesWithSeparators foreach writer.print
    }
  }
}

object JobClientUtils {
  // create a list of environment vars that need to be set in the job client
  // script
  def jobClientEnvVars(inputs: JobInputs, id: JobId, workDir: String, 
                       resourceAlloc: ResourceAlloc) =
    List(("HTRC_WORKING_DIR" -> workDir),
         ("HTRC_DEPENDENCY_DIR" -> HtrcConfig.dependencyDir),
         ("JAVA_CMD" -> HtrcConfig.javaCmd),
         ("JAVA_MAX_HEAP_SIZE" -> ("-Xmx" + resourceAlloc.javaMaxHeapSize)),
         ("HTRC_ALG_SCRIPT" -> inputs.runScript),
         ("HTRC_TIME_LIMIT" -> resourceAlloc.walltime),
         ("HTRC_JOBID" -> id.toString),
         ("HTRC_USER" -> inputs.user.submitter),
         ("HTRC_AGENT_ENDPOINT" -> HtrcConfig.agentEndpoint),
         ("HTRC_ID_SERVER_TOKEN_URL" -> HtrcConfig.idServerTokenUrl),
         ("HTRC_OAUTH_CLIENT_ID" -> HtrcConfig.agentClientId),
         ("HTRC_OAUTH_CLIENT_SECRET" -> HtrcConfig.agentClientSecret),
         ("HTRC_OAUTH_TOKEN" -> inputs.token))

  def jobClientEnvVars(inputs: JobInputs, id: JobId, workDir: String, 
                       timelimit: String) =
    List(("HTRC_WORKING_DIR" -> workDir),
         ("HTRC_DEPENDENCY_DIR" -> HtrcConfig.dependencyDir),
         ("JAVA_CMD" -> HtrcConfig.javaCmd),
         ("JAVA_MAX_HEAP_SIZE" -> HtrcConfig.javaMaxHeapSize),
         ("HTRC_ALG_SCRIPT" -> inputs.runScript),
         ("HTRC_TIME_LIMIT" -> timelimit),
         ("HTRC_JOBID" -> id.toString),
         ("HTRC_USER" -> inputs.user.submitter),
         ("HTRC_AGENT_ENDPOINT" -> HtrcConfig.agentEndpoint),
         ("HTRC_ID_SERVER_TOKEN_URL" -> HtrcConfig.idServerTokenUrl),
         ("HTRC_OAUTH_CLIENT_ID" -> HtrcConfig.agentClientId),
         ("HTRC_OAUTH_CLIENT_SECRET" -> HtrcConfig.agentClientSecret),
         ("HTRC_OAUTH_TOKEN" -> inputs.token))
}
