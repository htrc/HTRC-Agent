
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

// An actor that runs a task at the shell and sends status information to the
// supervisor. In this case the shell task is actually to do a remote job
// submission on a cluster which uses SLURM as the resource manager.

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

class SLURMTask(user: HtrcUser, inputs: JobInputs, id: JobId) extends Actor {

  import HtrcUtils._

  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  val registry = HtrcSystem.registry

  log.debug("SLURM_TASK_LAUNCHED\t{}\t{}\tJOB_ID: {}",
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
    val path = 
      workingDir + File.separator + HtrcConfig.systemVariables("output_dir")
    (new File(path)).mkdir()
    path
  }

  val target = HtrcConfig.computeResourceUser
  val targetWorkingDir = HtrcConfig.computeResourceWorkingDir
  val jobClientOutFile = targetWorkingDir + "/" + id + "/job-client.out"
  val jobClientErrFile = targetWorkingDir + "/" + id + "/job-client.err"

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

  // create job client script to launch AgentJobClient with the algorithm as
  // a sub-process
  // val timelimit = "-1"
  val timelimit = HtrcConfig.getPBSWalltime(inputs)
  val envVars = 
    JobClientUtils.jobClientEnvVars(inputs, id, 
                                    (targetWorkingDir + "/" + id), timelimit) ++
    List(("HTRC_MEANDRE_PORT" -> MeandrePortAllocator.get.toString))
  HtrcSystem.jobClientScriptCreator.
    createJobClientScript(envVars, workingDir + "/" + HtrcConfig.jobClientScript,
                          log)

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
      log.debug("SLURM_TASK_INPUTS_READY\t{}\t{}\tJOB_ID: {}\tWORKING_DIR: {}",
               user.name, inputs.ip, id, workingDir)

      try {
	// copy job working dir to compute resource
        copyToComputeResource()

        // launch job on compute resource
        val jobid = launchJob()
        supe ! StatusUpdate(InternalQueuedOnTarget)
      }
      catch {
        case JobSetupException(stdout, stderr) =>
          supe ! StatusUpdate(InternalCrashedWithError(stderr, stdout))
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

  // copy job working dir from local machine to compute resource; throws
  // JobSetupException upon error
  def copyToComputeResource() = {
    val scpOut = new StringBuilder
    val scpErr = new StringBuilder

    val scpLogger = SProcessLogger(
    (o: String) => 
      { log.debug("SLURM_SCP_TO_COMPUTE_RES_OUT\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                  user.name, inputs.ip, id, o)
        scpOut.append(o + "\n") },
    (e: String) => 
      { log.debug("SLURM_SCP_TO_COMPUTE_RES_ERR\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                  user.name, inputs.ip, id, e)
        scpErr.append(e + "\n") })

    val scpCmdF = "scp -r %s %s:%s/"
    val scpCmd = scpCmdF.format(workingDir, target, targetWorkingDir)
    val scpRes = SProcess(scpCmd) ! scpLogger

    log.debug("SLURM_SCP_TO_COMPUTE_RES\t{}\tJOB_ID: {}\tCMD: {}\tRESULT: {}", 
              user.name, id, scpCmd, scpRes)

    if (scpRes != 0) {
      val errorMsg = "Unable to copy job directory to compute resource.\n"
      throw JobSetupException(scpOut.toString, errorMsg + scpErr.toString)
    }
  }

  // launch job on compute resource; returns jobid upon success, and throws
  // JobSetupException upon error
  def launchJob(): String = {
    var jobid = ""
    val sbatchOut = new StringBuilder
    val sbatchErr = new StringBuilder
    // logger for sbatch; call to sbatch on cluster queues the job, and
    // writes jobid to stdout
    val sbatchLogger = SProcessLogger(
      (o: String) => 
        { // if sbatch is successful then the jobid is written to stdout
          jobid = extractSbatchJobId(o)
           // error msgs may also be written to stdout
          sbatchOut.append(o + "\n")
          log.debug("SLURM_SBATCH_OUT\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                    user.name, inputs.ip, id, o) },
      (e: String) => 
        { // "tcgetattr: Invalid argument" in stderr results from the use of
	  // the -t flag with ssh; this msg can be ignored
          if (!e.contains("tcgetattr: Invalid argument")) {
            sbatchErr.append(e + "\n")
            log.debug("SLURM_SBATCH_ERROR\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                      user.name, inputs.ip, id, e)
          }
        } )

    val cmdF = 
      "ssh -t -t -q %s sbatch %s -t %s -o %s -e %s %s/%s/%s"
    val cmd = cmdF.format(target, HtrcConfig.getSbatchOptions, timelimit, 
                          jobClientOutFile, jobClientErrFile, 
                          targetWorkingDir, id, HtrcConfig.jobClientScript)
    val sysProcess = SProcess(cmd, new File(workingDir))
    val exitVal = sysProcess ! sbatchLogger

    log.debug("SLURM_TASK_SBATCH_CMD\t{}\tJOB_ID: {}\tCMD: {}\tRESULT: {}",
	      user.name, id, cmd, exitVal)

    if (exitVal != 0) {
      val errorMsg = "Unable to launch job on compute resource (" + 
                     HtrcConfig.localResourceType + ").\n"
      throw JobSetupException(sbatchOut.toString, 
                              errorMsg + sbatchErr.toString)
    }
    else jobid
  }

  // extract the job id from the output of SLURM command sbatch
  def extractSbatchJobId(sbatchOut: String): String = {
    // the output of sbatch is a a string "Submitted batch job <jobid>"
    val sbatchOutPrefix = "Submitted batch job ";
    val i = sbatchOut.indexOf(sbatchOutPrefix)
    if (i >= 0) {
      sbatchOut.substring(i + sbatchOutPrefix.length)
    }
    else ""  
  }

  // This actor doesn't actually receive, be sad if it does.

  def receive = {
    case m =>
      log.error("SLURM task")
  }

}
