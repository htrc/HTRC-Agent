
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

// actor to create a new job for a user based on an existing job results
// folder in the cache

import akka.actor.{ Actor, Props, ActorRef, PoisonPill }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import akka.event.slf4j.Logger
import java.io._
import org.apache.commons.io.FileUtils

class CachedJob(inputs: JobInputs, id: JobId, 
                cachedJobId: String) extends Actor {
  import HtrcUtils._

  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)
  val auditLog = Logger("audit")

  val computeResource = "from cache"
  val sep = File.separator

  var userActor: ActorRef = null // HtrcAgent actor of the user that owns
				 // this job

  val token = inputs.token
  val userName = inputs.user.submitter
  val outputDir = HtrcConfig.systemVariables("output_dir")
  val relativeJobLoc = userName + sep + id
  val jobLocation = HtrcConfig.resultDir + sep + relativeJobLoc

  def jobResultFilePath(resultFile: String): String = {
    jobLocation + sep + outputDir + sep + resultFile
  }

  def jobResults: List[JobResult] = {
    // the copy of the cached job dir may or may not contain optional job
    // result files; we first determine the list of valid results
    val validResults =
      inputs.resultNames filter {x => fileExists(jobResultFilePath(x))}

    val dirResults = (validResults map { res =>
      DirectoryResult(relativeJobLoc + sep + outputDir + sep + res)
    }).toList

    val stderrFile = relativeJobLoc + sep + "stderr.txt"
    val stdoutFile = relativeJobLoc + sep + "stdout.txt"

    Stdout(stdoutFile) :: Stderr(stderrFile) :: dirResults
  }

  def createJobFolder(): Boolean = {
    val srcDir = HtrcConfig.cachedJobsDir + sep + cachedJobId
    val destDir = jobLocation

    try {
      // copy the cached job folder without preserving the date
      FileUtils.copyDirectory(new File(srcDir), new File(destDir), false)
      true
    } catch {
      case e: Exception =>
    	log.error("CACHED_JOB({}): exception in createJobFolder() {}", id, e)
        false
    }
  }

  def logEnd(t: String, totalTime: String) {
    // for audit log analyzer
    // type end_status request_id user ip token job_id job_name algorithm run_time
    val fstr = "CACHED_JOB_TERMINATION\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s"
    val str = fstr.format(t, inputs.requestId, userName, inputs.ip, inputs.token, 
                          id, inputs.name, inputs.algorithm, totalTime)
    auditLog.info(str)
  }

  val behavior: PartialFunction[Any,Unit] = {
    case m: JobMessage => {
      m match {
        case RunJob =>
          val startTime = java.lang.System.currentTimeMillis
          log.debug("CACHED_JOB({}): received RunJob", id)
	  userActor = sender // HtrcAgent sends RunJob to CachedJob
          val initStatus = Staging(inputs, id, computeResource)
	  userActor ! InternalUpdateJobStatus(id, initStatus, token)
          val succ = createJobFolder()
	  val finalStatus = 
            if (succ)
              Finished(inputs, id, computeResource, jobResults)
            else Crashed(inputs, id, computeResource, Nil)
	  userActor ! InternalUpdateJobStatus(id, finalStatus, token)

	  // for jobs running on local shell
          JobThrottler.removeJob()

          val endTime = java.lang.System.currentTimeMillis
          logEnd(if (succ) "FINISHED" else "CRASHED", 
                 ((endTime - startTime)/1000).toString)
	  self ! PoisonPill 

        case DeleteJob(id, token) =>
	  sender ! <success>Deleted job {id}</success>
	  self ! PoisonPill
	  log.info("CACHED_JOB({}): received DeleteJob", id)

        case StatusUpdate(status) =>
	  // unexpected msg; add to log
	  log.error("CACHED_JOB({}): unexpected msg, StatusUpdate({})", id,
		    status)
      }
    }
  }

  val unknown: PartialFunction[Any,Unit] = {
    case m => log.error("CACHED_JOB({}) received unexpected message {}", id, m)
  }

  def receive = behavior orElse unknown
}
