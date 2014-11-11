
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
import java.io.File
import java.io.PrintWriter
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

  // Build output loggers.
  val stdout = new StringBuilder
  val stderr = new StringBuilder
  val plogger = SProcessLogger(
    (o: String) => stdout.append(o + "\n"),
    (e: String) => stderr.append(e + "\n"))

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
    val path = workingDir + File.separator + HtrcConfig.systemVariables("output_dir")
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

  val dependenciesReady = inputs.dependencies map { case (path,name) =>
    (registry ? WriteFile(path, name, workingDir, inputs)).mapTo[WriteStatus]
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
        // HtrcUtils.writeFile("", "stdout.txt", user, id)
        var errorMsg = "Error in retrieving resource from the registry."
        e match {
          case RegistryError(resource) => 
            // HtrcUtils.writeFile("Error in retrieving " + resource +
            //                     " from the registry.", 
            //                     "stderr.txt", user, id)
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

      val envWorkingDir = ("HTRC_WORKING_DIR" -> (targetWorkingDir + "/" + id))
      val envDependencyDir = ("HTRC_DEPENDENCY_DIR" -> HtrcConfig.dependencyDir)
      val envJavaCmd = ("JAVA_CMD" -> HtrcConfig.javaCmd)
      val envJavaMaxHeapSize = 
        ("JAVA_MAX_HEAP_SIZE" -> HtrcConfig.javaMaxHeapSize)
      val envMeandrePort = 
        ("HTRC_MEANDRE_PORT" -> MeandrePortAllocator.get.toString)

      val cmdF = "bash %s/%s/%s"
      val cmd = cmdF.format(targetWorkingDir, id, inputs.runScript)

      // val env = 
      //   "HTRC_WORKING_DIR=" + targetWorkingDir + "/" + id + 
      //   " HTRC_DEPENDENCY_DIR=" + HtrcConfig.dependencyDir +
      //   " JAVA_CMD=" + HtrcConfig.javaCmd +
      //   " JAVA_MAX_HEAP_SIZE=" + HtrcConfig.javaMaxHeapSize +
      //   " HTRC_MEANDRE_PORT=" + MeandrePortAllocator.get.toString

      // val cmdF = "%s bash %s/%s/%s"
      // val cmd = cmdF.format(env, targetWorkingDir, id, inputs.runScript)

      println("Run job command: " + cmd)

      val sysProcess = SProcess(cmd, new File(HtrcConfig.homeDir), 
                                envWorkingDir, envDependencyDir, envJavaCmd, 
                                envJavaMaxHeapSize, envMeandrePort)

      // val sysProcess = SProcess(cmd, new File(HtrcConfig.homeDir))

      log.debug("SHELL_TASK_RUNNING_COMMAND\t{}\t{}\tJOB_ID: {}\tCOMMAND: {}",
               user.name, inputs.ip, id, cmd)
      
      supe ! StatusUpdate(InternalRunning)
      // Recall from above, this plogger forwards stdin and stdout to
      // the parent
      val exitCode = sysProcess ! plogger
      
      log.debug("SHELL_TASK_COMPLETE\t{}\t{}", id, exitCode)

      // start off by finding and creating the result directory if it
      // doesn't exist
      // val outputDir = HtrcConfig.systemVariables("output_dir")
      // val resultLocation = HtrcConfig.resultDir
      // val dest = resultLocation + "/" + user.name + "/" + id
      // log.debug("SHELL_TASK_CREATING_OUTPUTDIR\t{}\t{}", id, resultLocation)
      // (new File(dest)).mkdirs()

      // now cp the result folder back over
      // val resultCpCmdF = "cp -r %s/%s/%s %s"
      // val resultCpCmd = resultCpCmdF.format(targetWorkingDir, id, outputDir, dest)
      // log.debug("SHELL_TASK_RESULT_CP\t{}\t{}\tJOB_ID: {}\tCOMMAND: {}",
      //          user.name, inputs.ip, id, resultCpCmd)
      // val scpResultRes = SProcess(resultCpCmd) !

      // write the job's stderr, stdout to files in the result directory
      writeFile(stdout.toString, "stdout.txt", user, id)
      writeFile(stderr.toString, "stderr.txt", user, id)

      // create a list of the result files
      // val dirResults = inputs.resultNames map { n => 
      //   DirectoryResult(user.name+"/"+id+"/"+outputDir+"/"+n) }
      // log.debug("SHELL_TASK_RESULTS\t{}\t{}\tJOB_ID: {}\tRAW: {}",
      //          user.name, inputs.ip, id, dirResults)

      if(exitCode == 0) {
        // dirResults foreach { r =>
        //   supe ! Result(r)
        // }
        supe ! StatusUpdate(InternalFinished)        
      } else {
        supe ! StatusUpdate(InternalCrashed(true))
      }
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
