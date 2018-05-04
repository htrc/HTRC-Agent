
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
// submission on a cluster on which job scheduling is done using PBS.

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

class PBSTask(user: HtrcUser, inputs: JobInputs, id: JobId) extends Actor {
  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  val registry = HtrcSystem.registry
  val supe = parent

  // there are 3 job dirs used during job execution:
  // 1. local job dir (created before job is launched)
  // 2. job dir on the compute resource or target (used to run the job on the
  // compute resource)
  // 3. local result dir into which job result files, stdout.txt, stderr.txt
  // are copied from (2) once the job is completed

  // details about user and job dir on the compute resource
  val target = HtrcConfig.computeResourceUser
  val targetWorkingDir = HtrcConfig.computeResourceWorkingDir
  val jobClientOutFile = targetWorkingDir + "/" + id + "/job-client.out"
  val jobClientErrFile = targetWorkingDir + "/" + id + "/job-client.err"

  // val stdoutFile = targetWorkingDir + "/" + id + "/stdout.txt"
  // val stderrFile = targetWorkingDir + "/" + id + "/stderr.txt"

  // name of dir into which job result files are placed (not a complete path)
  val outputDir = HtrcConfig.systemVariables("output_dir")
  // local result dir
  val localResultDir = HtrcConfig.resultDir + "/" + user.name + "/" + id

  log.debug("PBS_TASK_LAUNCHED\t{}\t{}\tJOB_ID: {}", user.name, inputs.ip, id)
  parent ! StatusUpdate(InternalStaging)

  // At this point we might want to ... interact with the inputs. This
  // would be a set of Futures that when all resolved provide what we
  // need to start.

  // We should have all of the information on what inputs we need at
  // this point. First we fetch them, then write them to a working
  // directory.

  // Before we can write to a working directory, we must create one. This is
  // the local job dir created before the job is launched on the compute
  // resource.
  val workingDir = {
    // val root = "agent_working_directories"
    val root = HtrcConfig.localAgentWorkingDir

    (new File(root + File.separator + id)).mkdirs()
    root + File.separator + id
  }

  // Also create a result directory in the local job dir.
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
  val walltime = HtrcConfig.getPBSWalltime(inputs)
  val envVars = 
    JobClientUtils.jobClientEnvVars(inputs, id, (targetWorkingDir + "/" + id),
                                    walltime) ++
    List(("HTRC_MEANDRE_PORT" -> MeandrePortAllocator.get.toString))
  HtrcSystem.jobClientScriptCreator.
    createJobClientScript(envVars, workingDir + "/" + HtrcConfig.jobClientScript, 
    log)

  // Check if these things are all finished, once they are, continue.
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
      log.debug("PBS_TASK_INPUTS_READY\t{}\t{}\tJOB_ID: {}\tWORKING_DIR: {}",
                user.name, inputs.ip, id, workingDir)

      try {
	// copy job working dir to compute resource
        copyToComputeResource()

        // launch job on compute resource
        val jobid = launchJob()
        supe ! StatusUpdate(InternalQueuedOnTarget)

        // check job status until it is completed
        // jobExitStatus = checkJobStatus(jobid)

        // copy stderr.txt, stdout.txt from compute resource to local dir
        // copyFromComputeResource()

        // processJobResults()
	// if(jobExitStatus == 0)
        // supe ! StatusUpdate(InternalFinished)        
        // else supe ! StatusUpdate(InternalCrashed(true))
      }
      catch {
        case JobSetupException(stdout, stderr) => {
          supe ! StatusUpdate(InternalCrashedWithError(stderr, stdout))
          // HtrcUtils.writeFile(stdout, "stdout.txt", user, id)
          // HtrcUtils.writeFile(stderr, "stderr.txt", user, id)
          // supe ! StatusUpdate(InternalCrashed(false))
        }
      }
    }
  }

  // copy job working dir from local machine to compute resource; throws
  // JobSetupException upon error
  def copyToComputeResource() = {
    val scpOut = new StringBuilder
    val scpErr = new StringBuilder

    val scpLogger = SProcessLogger(
    (o: String) => 
      { log.debug("PBS_SCP_TO_COMPUTE_RES_OUT\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                  user.name, inputs.ip, id, o)
        scpOut.append(o + "\n") },
    (e: String) => 
      { log.debug("PBS_SCP_TO_COMPUTE_RES_ERR\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                  user.name, inputs.ip, id, e)
        scpErr.append(e + "\n") })

    val scpCmdF = "scp -r %s %s:%s/"
    val scpCmd = scpCmdF.format(workingDir, target, targetWorkingDir)
    val scpRes = SProcess(scpCmd) ! scpLogger

    log.debug("PBS_SCP_TO_COMPUTE_RES\t{}\tJOB_ID: {}\tCMD: {}\tRESULT: {}", 
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
    val qsubOut = new StringBuilder
    val qsubErr = new StringBuilder
    // logger for qsub; call to qsub on cluster queues the job, and writes jobid
    // to stdout; jobid is needed to check job status on cluster using qstat
    val qsubLogger = SProcessLogger(
      (o: String) => 
        { // if qsub is successful then the jobid is written to stdout
          jobid = o
           // error msgs may also be written to stdout
          qsubOut.append(o + "\n")
          log.debug("PBS_QSUB_OUT\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                    user.name, inputs.ip, id, o) },
      (e: String) => 
        { // "tcgetattr: Invalid argument" in stderr results from the use of
	  // the -t flag with ssh; this msg can be ignored
          if (!e.contains("tcgetattr: Invalid argument")) {
            qsubErr.append(e + "\n")
            log.debug("PBS_QSUB_ERROR\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                      user.name, inputs.ip, id, e)
          }
        } )

    // val env = 
    //   "HTRC_WORKING_DIR=" + targetWorkingDir + "/" + id + 
    //   " HTRC_DEPENDENCY_DIR=" + HtrcConfig.dependencyDir +
    //   " JAVA_CMD=" + HtrcConfig.javaCmd +
    //   " JAVA_MAX_HEAP_SIZE=" + HtrcConfig.javaMaxHeapSize

    // val walltime = HtrcConfig.getPBSWalltime(inputs)

    val cmdF = "ssh -t -t -q %s qsub -N %s %s %s " +
    "-l walltime=%s -o %s -e %s -V -W umask=0122 %s/%s/%s"
    val cmd = cmdF.format(target, qsubJobName, HtrcConfig.getQsubOptions,
      qsubProcessorReq, walltime, jobClientOutFile, jobClientErrFile,
      targetWorkingDir, id, HtrcConfig.jobClientScript)
    
    val sysProcess = SProcess(cmd, new File(workingDir))
    val exitVal = sysProcess ! qsubLogger

    log.debug("PBS_TASK_QSUB_CMD\t{}\tJOB_ID: {}\tCMD: {}\tRESULT: {}",
	      user.name, id, cmd, exitVal)

    if (exitVal != 0) {
      val errorMsg = "Unable to launch job on compute resource (" + 
                     HtrcConfig.localResourceType + ").\n"
      throw JobSetupException(qsubOut.toString, errorMsg + qsubErr.toString)
    }
    else jobid
  }

  // return a job name of the form <username>:<algorithmName>
  def qsubJobName: String = {
    // the qsub job name has the following form
    //   (7 chars of username):algName
    val maxLenUserName = 7
    val divider = ":"

    (user.name.substring(0, maxLenUserName.min(user.name.length)) + divider +
      algorithmNameForQsub)
  }

  // obtain a short name for the algorithm to be used in the job name in qsub
  def algorithmNameForQsub: String = {
    val algName = inputs.info \ "short_name"
    if (algName.isEmpty) (inputs.info \ "name" text) else algName.text
  }

  // return a string containing the processor request for this job
  def qsubProcessorReq: String = {
    // obtain the no. of processors specified in the algorithm XML file, if any
    val numProcs =
      inputs.system.metadata \ "execution_info" \ "number_of_processors"
  
    HtrcConfig.getQsubProcReqStr + (if (numProcs.isEmpty)
      HtrcConfig.getDefaultNumProcessors else numProcs.text)
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
          // no spaces around '=' to handle algorithms that use "source
          // <algorithm>.properties" to define properties as env vars
          if (v != "HTRC_DEFAULT") p.println(k + "=" + v) }
      })
    }
  }

  // This actor doesn't actually receive, be sad if it does.

  def receive = {
    case m =>
      log.error("PBS task")
  }

}
