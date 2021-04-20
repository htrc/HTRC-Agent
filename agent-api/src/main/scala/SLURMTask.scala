
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
// submission on a cluster on which job scheduling is done using SLURM.

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
import scala.collection.Map
import scala.util.{Success, Failure}

import scala.xml._
import scala.util.Try

class SLURMTask(user: HtrcUser, inputs: JobInputs, id: JobId) extends Actor {
  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  val registry = HtrcSystem.registry
  val supe = parent

  val dummyWorksetMd =
    <workset xmlns="http://registry.htrc.i3.illinois.edu/entities/workset">
      <metadata>
        <name>DUMMY_WORKSET_SHOULD_NOT_BE_SEEN</name>
        <author>DUMMY_AUTHOR</author>
        <volumeCount>0</volumeCount>
      </metadata>
    </workset>

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

  log.debug("SLURM_TASK_LAUNCHED\t{}\t{}\tJOB_ID: {}", user.name, inputs.ip, id)
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
      val lsOptWksetMd = inputs.lsCollectionMetadata
      // Future.sequence(lsFutureWksetMetadata) map { lsOptWksetMd =>
      logWorksetMetadata(lsOptWksetMd)

      // get the list of workset names for which metadata could not be
      // retrieved from the registry
      val wksetsWithNoMetadata = lsOptWksetMd filter {
        case(wkset, optWksetMd) => optWksetMd.isEmpty
      } map { _._1 }

      // if there is an error in retrieving metadata for any workset, then
      // the job submission fails
      if (wksetsWithNoMetadata.length > 0) {
        val errorMsg =
          "Error in retrieving metadata for workset(s) from the registry: " +
        wksetsWithNoMetadata.mkString(", ")
        supe ! StatusUpdate(InternalCrashedWithError(errorMsg, ""))
      } else {
        // map workset names to WorksetMetadata
        val mapWorksetMetadata = lsOptWksetMd map { case(wkset, optWksetMd) =>
          (wkset, optWksetMd.getOrElse(WorksetMetadata(dummyWorksetMd)))
          // dummyWorksetMd is not strictly required since at this point
          // all Options in the list are non-empty
        } toMap

        // check if there are any errors in the input worksets, e.g.,
        // workset size exceeds the size limit for the workset for the
        // algorithm in the job submission
        val lsErrorWksets = getErrorWorksets(mapWorksetMetadata)
        if (!lsErrorWksets.isEmpty) {
          supe ! StatusUpdate(InternalCrashedWithError(messageForErrorWorksets(lsErrorWksets), ""))
        } else {
          // sum up the sizes of all input worksets
          val totalInputSize =
            lsOptWksetMd.foldLeft(0) ( (sum, tuple) =>
              sum + (tuple match {
                case (wksetName, optWksetMd) =>
                  optWksetMd map { _.volumeCount } getOrElse(0)
                    // at this point, optWksetMd is non-empty (i.e., it is
                    // not None), since this case has already been checked and
                    // handled earlier; the "getOrElse(0)" is just a precaution
              }))

          inputs.system.parseExecInfo() match {
            case Failure(e) =>
              val errorMsg = "Error in algorithm XML.\n" + e
              supe ! StatusUpdate(InternalCrashedWithError(errorMsg, ""))

            case Success(lsResAlloc) =>
              // logResAlloc(lsResAlloc)
              getResourceAlloc(lsResAlloc, totalInputSize) match {
                case Left(errorMsg) =>
                  supe ! StatusUpdate(InternalCrashedWithError(errorMsg, ""))

                case Right(resourceAlloc) =>
                  log.debug("Resource allocation for this job: {}",
                    resourceAlloc.toString)

                  val envVars =
                    JobClientUtils.jobClientEnvVars(inputs, id,
                      (targetWorkingDir + "/" + id), resourceAlloc) ++
                  List(("HTRC_MEANDRE_PORT" ->
                    MeandrePortAllocator.get.toString))
                  HtrcSystem.jobClientScriptCreator.
                    createJobClientScript(envVars,
                      workingDir + "/" + HtrcConfig.jobClientScript,
                      log)

                  // to be here we must have not had errors, so do the work
                  log.debug("SLURM_TASK_INPUTS_READY\t{}\t{}\tJOB_ID: {}\tWORKING_DIR: {}",
                    user.name, inputs.ip, id, workingDir)

                  try {
	            // copy job working dir to compute resource
                    copyToComputeResource()

                    // launch job on compute resource
                    val jobSubmissionResult = launchJob(resourceAlloc)
                    supe ! StatusUpdate(InternalQueuedOnTarget)

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
        }
      }
      // }
    }
  }

  // copy job working dir from local machine to compute resource; throws
  // JobSetupException upon error
  def copyToComputeResource() = {
    val scpOut = new StringBuilder
    val scpErr = new StringBuilder

    val scpLogger = SProcessLogger(
    (o: String) => 
      { log.info("SCP_TO_COMPUTE_RES_OUT\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                  user.name, inputs.ip, id, o)
        scpOut.append(o + "\n") },
    (e: String) => 
      { log.info("SCP_TO_COMPUTE_RES_ERR\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                  user.name, inputs.ip, id, e)
        scpErr.append(e + "\n") })

    val scpCmdF = "scp -r %s %s:%s/"
    val scpCmd = scpCmdF.format(workingDir, target, targetWorkingDir)
    val scpRes = SProcess(scpCmd) ! scpLogger

    log.info("SCP_TO_COMPUTE_RES\t{}\tJOB_ID: {}\tCMD: {}\tRESULT: {}", 
              user.name, id, scpCmd, scpRes)

    if (scpRes != 0) {
      val errorMsg = "Unable to copy job directory to compute resource.\n"
      throw JobSetupException(scpOut.toString, errorMsg + scpErr.toString)
    }
  }

  // launch job on compute resource; returns the resuly of job submission
  // upon success, and throws JobSetupException upon error
  def launchJob(resourceAlloc: ResourceAlloc): String = {
    var jobSubmissionResult = ""
    val batchSubmitOut = new StringBuilder
    val batchSubmitErr = new StringBuilder
    // logger for batch submission; batch submission on cluster queues the
    // job, and writes a success or error code to stdout (0 for success)
    val batchSubmitLogger = SProcessLogger(
      (o: String) => 
        { // if sbatch is successful then it returns 0, otherwise an error code
          jobSubmissionResult = o
           // error msgs may also be written to stdout
          batchSubmitOut.append(o + "\n")
          log.info("SLURM_SBATCH_OUT\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                    user.name, inputs.ip, id, o) },
      (e: String) => 
        { // "tcgetattr: Invalid argument" in stderr results from the use of
	  // the -t flag with ssh; this msg can be ignored
          if (!e.contains("tcgetattr: Invalid argument")) {
            batchSubmitErr.append(e + "\n")
            log.info("SLURM_SBATCH_ERROR\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                      user.name, inputs.ip, id, e)
          }
        } )

    // "-W umask=0122" used in qsub cmd is elided below
    val cmdF = "ssh -t -t -q %s sbatch -J %s -D %s %s --time=%s " +
    "--mem=%s --output=%s --error=%s --export=ALL  %s/%s/%s"
    val cmd = cmdF.format(target, constructJobName, targetWorkingDir + "/" + id,
      processorReqString(resourceAlloc), resourceAlloc.walltime,
      memoryInMB(resourceAlloc.vmem), jobClientOutFile,
      jobClientErrFile, targetWorkingDir, id, HtrcConfig.jobClientScript)
    
    val sysProcess = SProcess(cmd, new File(workingDir))
    val exitVal = sysProcess ! batchSubmitLogger

    log.info("SLURM_TASK_SBATCH_CMD\t{}\tJOB_ID: {}\tCMD: {}\tRESULT: {}",
	      user.name, id, cmd, exitVal)

    if (exitVal != 0) {
      val errorMsg = "Unable to launch job on compute resource (" + 
                     HtrcConfig.localResourceType + ").\n"
      throw JobSetupException(batchSubmitOut.toString,
        errorMsg + batchSubmitErr.toString)
    }
    else jobSubmissionResult
  }

  // return a job name of the form <username>:<algorithmName>
  def constructJobName: String = {
    // the job name has the following form
    //   (7 chars of username):algName
    val maxLenUserName = 7
    val divider = ":"

    (user.name.substring(0, maxLenUserName.min(user.name.length)) + divider +
      algorithmNameForBatchProcess)
  }

  // obtain a short name for the algorithm to be used in the job name
  def algorithmNameForBatchProcess: String = {
    val algName = inputs.info \ "short_name"
    if (algName.isEmpty) (inputs.info \ "name" text) else algName.text
  }

  // return a string containing the processor request for this job
  def processorReqString(resourceAlloc: ResourceAlloc): String = {
    "--nodes=" + resourceAlloc.numNodes + " --ntasks-per-node=" +
    resourceAlloc.numProcsPerNode
  }

  // converts strings like "16gb" to "16000", representing memory in MB; will
  // not work for a string that is not of the form "<number>gb"; this is
  // intended for conversion from Torque specifications of vmem to SLURM
  // specifications of mem in MB; at present, all config settings for Torque
  // are in the form of <number>gb
  def memoryInMB(mem: String): String = {
    if (mem.endsWith("gb")) mem.replace("gb", "000") else mem
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

  // given a map of workset names associated with workset metadata, returns a
  // list of (worksetName, worksetSize, worksetSizeLimitForAlgorithm) tuples,
  // where worksetSizeLimitForAlgorithm is the size limit on the workset
  // param for the algorithm in the job submission corresponding to this
  // actor, as specified in "inputs"
  def getWorksetSizesAndLimits(mapWorksetMetadata: Map[String, WorksetMetadata]): Seq[(String, Int, Int)] = {
    (inputs.user.arguments \ "parameters" \ "param") filter { p =>
      (p \ "@type" text) == "collection"
    } map { p =>
      val wksetParamName = (p \ "@name" text)
      val wksetParamValue = (p \ "@value" text)
      val size = mapWorksetMetadata.get(wksetParamValue).
        map(_.volumeCount).getOrElse(-1)
      val sizeLimit =
        inputs.system.worksetSizeLimits.getOrElse(wksetParamName, -1)
      ((p \ "@value" text), size, sizeLimit)
    }
  }

  // given a map of workset names associated with workset metadata, returns a
  // list of tuples with unexpected sizes, or where the size exceeds the size
  // limit specified for the workset parameter for the algorithm in this job
  // submission
  def getErrorWorksets(mapWorksetMetadata: Map[String, WorksetMetadata]): Seq[(String, Int, Int)] = {
    val lsWorksetSizesNLimits = getWorksetSizesAndLimits(mapWorksetMetadata)
    lsWorksetSizesNLimits filter { case(wkset, size, sizeLimit) =>
      (size < 0) || ((sizeLimit >= 0) && (size > sizeLimit))
    }
  }

  // given a list of (wksetName, size, sizeLimit) tuples, returns a composite
  // error message, containing relevant errors for each workset
  def messageForErrorWorksets(lsWorksetSizesNLimits: Seq[(String, Int, Int)]): String = {
    lsWorksetSizesNLimits map { case(wkset, size, sizeLimit) =>
      if (size < 0)
        "Unexpected size of workset " + wkset + ": " + size
      else if ((sizeLimit >= 0) && (size > sizeLimit))
        "Size of workset " + wkset + " (" + size + ") exceeds the size limit (" +
        sizeLimit + ") for this algorithm."
    } mkString("", "\n", "\n")
  }

  // write workset metadata to the log
  def logWorksetMetadata(lsOptWksetMd: List[(String, Option[WorksetMetadata])]): Unit = {
    val lsStringWksetMd = lsOptWksetMd map { case(wkset, optWksetMd) =>
      "(" + wkset + ", " +
      optWksetMd.map(_.volumeCount).getOrElse("NO_METADATA_AVAILABLE") + ")"
    }
    log.debug("WORKSET METADATA: {}", lsStringWksetMd.mkString(", "))
  }

  // given a sequence of (min, max, resourceAlloc) tuples, and an inputSize,
  // returns the ResourceAlloc object corresponding to the one in the
  // sequence whose [min, max] range contains inputSize; if there is no such
  // object, or if there are multiple objects, then a suitable error message
  // is returned
  def getResourceAlloc(ls: Seq[(Int, Int, ResourceAlloc)], inputSize: Int):
      Either[String, ResourceAlloc] = {
    val matchedItems = ls filter { case (min, max, ra) =>
      ((min <= inputSize) && (inputSize <= max))
    }

    if (matchedItems.isEmpty) {
      // Left("No resource allocation for input size " + inputSize +
      //   " in algorithm XML")
      val ra = getDefaultResourceAlloc
      log.debug("Using default resource allocation for job {}: {}", id.toString, ra)
      Right(ra)
    }
    else if (matchedItems.size > 1)
      Left("Multiple (conflicting) resource allocations for input size " +
        inputSize + " in algorithm XML")
    else 
      matchedItems(0) match { case(min, max, ra) => Right(ra) }
  }

  def getDefaultResourceAlloc: ResourceAlloc = {
    ResourceAlloc(HtrcConfig.getDefaultNumNodes,
      HtrcConfig.getDefaultNumProcessorsPerNode,
      HtrcConfig.getDefaultWalltime,
      HtrcConfig.getDefaultJavaMaxHeapSize,
      HtrcConfig.getDefaultVmem)
  }

  def logResAlloc(ls: Seq[(Int, Int, ResourceAlloc)]): Unit = {
    log.debug("Resource allocations: {}", 
      ls map { case (min, max, ra) =>
        "(" + min + ", " + max + ", " + ra + ")"
      } mkString(", "))
  }

  // This actor doesn't actually receive, be sad if it does.

  def receive = {
    case m =>
      log.error("SLURM task")
  }

}

