
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

// LocalJobCompletion: performs end-of-job actions for jobs run on the local
// machine, i.e., the same machine as the agent

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

class LocalJobCompletion(val status: PendingCompletion,
                         val token: String) extends JobCompletionTask {
  def copyJobResultsFromCompRes(itemsToCopy: String): Int = {
    val cpOut = new StringBuilder
    val cpErr = new StringBuilder

    val cpLogger = SProcessLogger(
      (o: String) => cpOut.append(o + "\n"),
      (e: String) => cpErr.append(e + "\n"))

    // create the folder for job results
    (new File(localResultDir)).mkdirs() 

    // now cp the result folder back over
    val resultCpCmdF = "cp -r %s %s"
    val resultCpCmd = resultCpCmdF.format(itemsToCopy, localResultDir)
    val cpRes = SProcess(resultCpCmd) ! cpLogger

    log.debug("CP_JOB_RESULTS\t{}\tJOB_ID: {}\tCMD: {}\tRESULT: {}",
    	      userName, id, resultCpCmd, cpRes)

    if (cpRes != 0) {
      val errorMsg = 
        "Unable to copy some/all results to the job results directory.\n"
      val pathPrefix = localResultDir + "/"
      HtrcUtils.appendToStdOutErrFile(cpOut.toString, 
                                      pathPrefix + "stdout.txt")
      HtrcUtils.appendToStdOutErrFile(errorMsg + cpErr.toString,
                                      pathPrefix + "stderr.txt")
    }
    cpRes
  }

  def copyJobResultsFromCompRes: Int = {
    val targetWorkingDir = HtrcConfig.targetWorkingDir(status.computeResource)
    val stdoutFile = targetWorkingDir + "/" + id + "/stdout.txt"
    val stderrFile = targetWorkingDir + "/" + id + "/stderr.txt"

    val itemsToCopyF = "%s/%s/%s %s %s"
    val itemsToCopy = 
      itemsToCopyF.format(targetWorkingDir, id, outputDir, 
                          stdoutFile, 
                          stderrFile)

    copyJobResultsFromCompRes(itemsToCopy)
  }

  def copyJobResultsFromCompResOnTimeOut: Int = {
    val targetWorkingDir = HtrcConfig.targetWorkingDir(status.computeResource)
    val stdoutFile = targetWorkingDir + "/" + id + "/stdout.txt"
    val stderrFile = targetWorkingDir + "/" + id + "/stderr.txt"

    val itemsToCopyF = "%s/%s/%s %s %s"
    val itemsToCopy = 
      itemsToCopyF.format(targetWorkingDir, id, outputDir, 
                          stdoutFile, 
                          stderrFile)

    copyJobResultsFromCompRes(itemsToCopy)
  }
}
