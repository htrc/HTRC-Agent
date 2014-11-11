
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

// RemoteJobCompletion: performs end-of-job actions for jobs run on remote
// machines

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import akka.event.slf4j.Logger
import java.io.File
import java.io.PrintWriter
import akka.pattern.ask
import scala.concurrent._
import scala.sys.process.{ Process => SProcess }
import scala.sys.process.{ ProcessLogger => SProcessLogger }

class RemoteJobCompletion(val status: PendingCompletion,
                          val token: String) extends JobCompletionTask {
  def copyJobResultsFromCompRes(itemsToCopy: String): Int = {
    val scpOut = new StringBuilder
    val scpErr = new StringBuilder

    val scpLogger = SProcessLogger(
    (o: String) => 
      { log.debug("SCP_FROM_COMPUTE_RES_OUT\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                  userName, inputs.ip, id, o)
        scpOut.append(o + "\n") },
    (e: String) => 
      { log.debug("SCP_FROM_COMPUTE_RES_ERR\t{}\t{}\tJOB_ID: {}\tMSG: {}",
                  userName, inputs.ip, id, e)
        scpErr.append(e + "\n") })

    (new File(localResultDir)).mkdirs() 

    val scpCmdF = "scp -r %s %s"
    val scpCmd = scpCmdF.format(itemsToCopy, localResultDir)
    val scpRes = SProcess(scpCmd) ! scpLogger

    log.debug("SCP_JOB_RESULTS\t{}\tJOB_ID: {}\tCMD: {}\tRESULT: {}",
	      userName, id, scpCmd, scpRes)

    if (scpRes != 0) {
      val errorMsg = "Unable to copy some/all results from compute resource (" + 
                     computeResource + ").\n"
      // processJobResults()
      val pathPrefix = localResultDir + "/"
      HtrcUtils.appendToStdOutErrFile(scpOut.toString, 
                                      pathPrefix + "stdout.txt")
      HtrcUtils.appendToStdOutErrFile(errorMsg + scpErr.toString,
                                      pathPrefix + "stderr.txt")
    }
    scpRes
  }

  def copyJobResultsFromCompRes: Int = {
    val target = HtrcConfig.targetUser(status.computeResource)
    val targetWorkingDir = HtrcConfig.targetWorkingDir(status.computeResource)
    val stdoutFile = targetWorkingDir + "/" + id + "/stdout.txt"
    val stderrFile = targetWorkingDir + "/" + id + "/stderr.txt"

    val jobClientOutFile = targetWorkingDir + "/" + id + "/job-client.out"
    val jobClientErrFile = targetWorkingDir + "/" + id + "/job-client.err"

    val itemsToCopyF = "%s:%s/%s/%s %s:%s %s:%s %s:%s %s:%s"
    val itemsToCopy = 
      itemsToCopyF.format(target, targetWorkingDir, id, outputDir, 
                          target, stdoutFile, 
                          target, stderrFile, 
                          target, jobClientOutFile, 
                          target, jobClientErrFile)

    copyJobResultsFromCompRes(itemsToCopy)
  }

  def copyJobResultsFromCompResOnTimeOut: Int = {
    val target = HtrcConfig.targetUser(status.computeResource)
    val targetWorkingDir = HtrcConfig.targetWorkingDir(status.computeResource)
    val stdoutFile = targetWorkingDir + "/" + id + "/stdout.txt"
    val stderrFile = targetWorkingDir + "/" + id + "/stderr.txt"

    val itemsToCopyF = "%s:%s/%s/%s %s:%s %s:%s"
    val itemsToCopy = 
      itemsToCopyF.format(target, targetWorkingDir, id, outputDir, 
                          target, stdoutFile, 
                          target, stderrFile)

    copyJobResultsFromCompRes(itemsToCopy)
  }
}
