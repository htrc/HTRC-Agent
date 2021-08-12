
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

// An actor that runs a task at the shell and sends status information
// to the supervisor. In this case the shell task is actually to do a
// remote job submission on Odin.

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import akka.event.slf4j.Logger
import scala.sys.process.{ Process => SProcess }
import scala.sys.process.{ ProcessLogger => SProcessLogger }
import scala.sys.process.{ ProcessBuilder => SProcessBuilder }
import scala.sys.process.ProcessIO
import java.io.File
import java.io.PrintWriter
import java.io.InputStream
import akka.pattern.ask
import scala.concurrent._
import scala.collection.mutable.HashMap

class ShellTask(user: HtrcUser, inputs: JobInputs, id: JobId) extends Actor {

  import HtrcUtils._

  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  val registry = HtrcSystem.registry

  log.debug("SHELL_TASK_LAUNCHED\t{}\t{}\tJOB_ID: {}",
           user.name, inputs.ip, id)

  parent ! StatusUpdate(InternalStaging)

  // name of dir into which job result files are placed (not a complete path)
  val outputDir = HtrcConfig.systemVariables("output_dir")

  // At this point we might want to ... interact with the inputs. This
  // would be a set of Futures that when all resolved provide what we
  // need to start.

  // We should have all of the information on what inputs we need at
  // this point. First we fetch them, then write them to a working
  // directory.

  // Before we can write to a working directory, we must create one.
  val workingDir = {
    // val root = "agent_working_directories"
    val root = HtrcConfig.localAgentWorkingDir

    (new File(root + File.separator + id)).mkdirs()
    root + File.separator + id
  }

  // Also create the result directory.
  val resultDir = {
    val path = workingDir + File.separator + outputDir
    (new File(path)).mkdir()
    path
  }

  // Write the properties.
  writeProperties(inputs.properties, inputs.propertiesFileName, workingDir)

  // To fetch information from the registry we send it a message with
  // both the registry path and the output path. Instead of responding
  // with the information it writes it out to disk for us. The
  // response we get back is an indication that is has finished.

  // The outputs we need to write are the collections, and the
  // dependencies stored in the registry. Dependencies currently
  // include files.

  val dependenciesReady = inputs.dependencies map { name =>
    (registry ? WriteFile(name, workingDir, inputs)).mapTo[WriteStatus]
  } toList

  // We do the same thing with collections, but our command is
  // different.
  
  val collectionsReady = inputs.collections map { c =>
    (registry ? WriteCollection(c, workingDir, inputs)).mapTo[WriteStatus]
  } toList

  // Check if these things are all finished, once they are, continue.

  val supe = parent

  Future.sequence(dependenciesReady ++ collectionsReady) map { statuses =>
    val errors = statuses.collect( _ match { case v @ RegistryError(e) => v })
    if(errors.length != 0) {
      errors.foreach { e =>
        log.error("Registry failed to write resource: " + e)
        var errorMsg = "Error in retrieving resource from the registry."
        e match {
          case RegistryError(resource) => 
            errorMsg = "Error in retrieving " + resource + " from the registry."
        }
        supe ! StatusUpdate(InternalCrashedWithError(errorMsg, ""))
      }
    } else {
      // to be here we must have not had errors, so do the work

      log.debug("SHELL_TASK_INPUTS_READY\t{}\t{}\tJOB_ID: {}",
               user.name, inputs.ip, id)

      log.debug("SHELL_TASK_WORKING_DIR\t{}\t{}\tJOB_ID: {}\tWORKING_DIR: {}",
               user.name, inputs.ip, id, workingDir)

      val targetWorkingDir = HtrcConfig.computeResourceWorkingDir
      val jobClientOutFile = targetWorkingDir + "/" + id + "/job-client.out"
      val jobClientErrFile = targetWorkingDir + "/" + id + "/job-client.err"

      // create job client script to launch AgentJobClient with the algorithm
      // as a sub-process
      val walltime = HtrcConfig.maxWalltime
      val envVars = 
        JobClientUtils.jobClientEnvVars(inputs, id, 
                                        (targetWorkingDir + "/" + id),
                                        walltime) ++
        List(("HTRC_MEANDRE_PORT" -> MeandrePortAllocator.get.toString))
      HtrcSystem.jobClientScriptCreator.
        createJobClientScript(envVars, 
                              workingDir + "/" + HtrcConfig.jobClientScript, 
                              log)

      // command to launch the AgentJobClient process 
      val cmdF = "bash %s/%s/%s"
      val cmd = cmdF.format(targetWorkingDir, id, HtrcConfig.jobClientScript)

      val sysProcess = SProcess(cmd)

      log.debug("SHELL_TASK_RUNNING_COMMAND\t{}\t{}\tJOB_ID: {}\tCOMMAND: {}",
               user.name, inputs.ip, id, cmd)

      def inputStreamToFile(outputFile: String)(is: InputStream) = {
        val sb = new StringBuilder
        scala.io.Source.fromInputStream(is).getLines.foreach(l => 
          sb.append(l + "\n"))
        appendToFile(sb.toString, outputFile)
      }

      val pio = new ProcessIO(_ => (),
                              inputStreamToFile(jobClientOutFile),
                              inputStreamToFile(jobClientErrFile))
      
      // run the AgentJobClient process in the background, so that this actor
      // does not block on the job
      val proc = sysProcess.run(pio)
    }
  }

  // Helper to write properties file to disk

  def writeProperties(properties: HashMap[String,String],
                      name: String,
                      workingDir: String) {

    def printToFile(f: File)(op: PrintWriter => Unit) {
      val p = new PrintWriter(f)
      try { op(p) } finally { p.close() }
    }

    if(properties.isEmpty == false) {
      printToFile(new File(workingDir + File.separator + name))(p => {
        properties foreach { case (k,v) =>
          if(v != "HTRC_DEFAULT") p.println(k + " = " + v) }
      })
    }
  }

  // This actor doesn't actually receive, be sad if it does.

  def receive = {
    case m =>
      log.error("shell task")
  }

}
