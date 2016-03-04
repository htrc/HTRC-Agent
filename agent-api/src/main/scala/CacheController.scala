
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

// actor that supports caching of job results; reads peristent cache index on
// startup, performs cache lookups and addition of cache entries on request,
// and performs periodic writes of the cache index

import java.io.File
import akka.actor.{ Actor, Props }
import akka.util.Timeout
import akka.event.Logging
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.pipe
import scala.sys.process.{ Process => SProcess }
import org.apache.commons.io.FileUtils

class CacheController extends Actor {
  // context import
  import context._

  // timeout for futures
  implicit val timeout = Timeout(30 seconds)

  // logging
  val log = Logging(context.system, this)

  val jobResultCache = new JobResultCache(HtrcConfig.cacheSize)

  // schedule periodic writes of the cache index to disk
  val writeInterval = HtrcConfig.cacheWriteInterval
  log.debug("CACHE_CONTROLLER: cacheWriteInterval = {} seconds, " + 
            "initCacheSize = {}, maxCacheSize = {}, cacheJobs = {}", 
            writeInterval, jobResultCache.size, HtrcConfig.cacheSize, 
            HtrcConfig.cacheJobs)

  val behavior: PartialFunction[Any,Unit] = {
    case m: CacheControllerMessage => 
      m match {
        case GetJobFromCache(js, token) =>
          log.debug("CACHE_CONTROLLER received GetJobFromCache({}, {})", js, 
                    token)

        case GetDataForJobRun(js, token) =>
          val currSender = sender
          log.debug("CACHE_CONTROLLER received GetDataForJobRun({}, {})", js, 
                    token)
          
          RegistryHttpClient.algorithmMetadata(js.algorithm, token) map {
            algMetadata =>
              // if HtrcConfig.cacheJobs is set to false, then there is no
              // need for the key
	      if (!HtrcConfig.cacheJobs)
                currSender ! DataForJobRun(None, algMetadata)
              else {
                JobResultCache.constructKey(js, algMetadata, token) map {
                  jobKey => currSender ! DataForJobRun(jobKey, algMetadata)
                }
	      }
          }

	case AddJobToCache(key, jobStatus) =>
	  log.debug("CACHE_CONTROLLER received AddJobToCache({}, {})", key, 
                    jobStatus.id)
          // if cacheJobs is false, or if the cache already contains the key,
          // or if the "finished" job does not have all expected results,
          // then do not add the job to the cache; this point in the code
          // might be reached even if the cache contains the key, for
          // instance when usecache for this job's "/algorithm/run" is false,
          // and HtrcConfig.cacheJobs is true
          if (HtrcConfig.cacheJobs &&
              (!jobResultCache.contains(key)) && 
              allJobResultsAvailable(jobStatus)) {
            copyJobToCacheDir(jobStatus) foreach { cachedJobId => 
              jobResultCache.put(key, cachedJobId)
            }
	  } else {
            log.debug("CACHE_CONTROLLER: cacheJobs is false, " + 
                      "or cache already contains key, or " + 
                      "job to be cached has missing job results; " + 
                      "not caching {} job {}",
                      jobStatus.algorithm, jobStatus.id)
	  }

	case WriteCacheToFile =>
          log.debug("CACHE_CONTROLLER received WriteCacheToFile")
	  jobResultCache.writeCacheToFileIfNeeded
          system.scheduler.scheduleOnce(writeInterval seconds, self, 
                                        WriteCacheToFile)

      }
  }

  val unknown: PartialFunction[Any,Unit] = {
    case m =>
      log.error("Cache controller received unhandled message")
  }

  // returns true if all expected job results (specified in the algorithm
  // metadata) exist in the job result folder, false otherwise
  def allJobResultsAvailable(jobStatus: Finished): Boolean = {
    val sep = File.separator
    val jobResultSubdir = HtrcConfig.resultDir + sep + jobStatus.jobResultLoc +
                          sep + HtrcConfig.systemVariables("output_dir")
    jobStatus.inputs.resultNames forall { res => 
      log.debug("CACHE_CONTROLLER: looking for {}", jobResultSubdir + sep + res)
      HtrcUtils.fileExists(jobResultSubdir + sep + res)
    }
  }

  // create a new job folder in HtrcConfig.cachedJobsDir which is a copy of
  // the job result folder of the given job, and return
  // Some(newJobFolderName); return None if the copy is unsuccessful
  def copyJobToCacheDir(jobStatus: Finished): Option[String] = {
    val sep = File.separator
    val cachedJobId = HtrcUtils.newJobId
    val srcDir = HtrcConfig.resultDir + sep + jobStatus.jobResultLoc
    val destDir = HtrcConfig.cachedJobsDir + sep + cachedJobId

    log.debug("CACHE_CONTROLLER: copyJobToCacheDir, src {}, dest {}", srcDir, 
	      destDir) 

    try {
      FileUtils.copyDirectory(new File(srcDir), new File(destDir))
      Some(cachedJobId)
    } catch {
      case e: Exception => 
	log.debug("CACHE_CONTROLLER: exception in copyJobToCacheDir {}", e)
        None
    }
  }

  override def preStart(): Unit = {
    // send WriteCacheToFile msg to self after specified time interval to
    // ensure that the cache is periodically written to disk
    system.scheduler.scheduleOnce(writeInterval seconds, self, 
				  WriteCacheToFile)
  }

  def receive = behavior orElse unknown

}

// response for message GetDataForJobRun; algMetadata contains details needed
// to launch the job; the jobResultCacheKey is used if the results of the job
// after execution are to be cached
case class DataForJobRun(jobResultCacheKey: Option[String], 
                         algMetadata: JobProperties)
