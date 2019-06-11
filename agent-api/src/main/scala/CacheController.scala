
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
import org.apache.commons.io.filefilter.DirectoryFileFilter
import scala.concurrent.Future

class CacheController extends Actor {
  // context import
  import context._

  // timeout for futures
  implicit val timeout = Timeout(30 seconds)

  // logging
  val log = Logging(context.system, this)

  val sep = File.separator

  val jobResultCache = new JobResultCache(HtrcConfig.cacheSize)
  jobResultCache.readCacheFromFile(HtrcConfig.cacheFilePath)
  // remove orphaned cached job folders if so specified
  if (HtrcConfig.cleanUpCachedJobsOnStartup)
    cleanUpCachedJobsFolder()

  // schedule periodic writes of the cache index to disk
  val writeInterval = HtrcConfig.cacheWriteInterval
  log.debug("CACHE_CONTROLLER: cacheWriteInterval = {} seconds, " + 
            "initCacheSize = {}, maxCacheSize = {}, cacheJobs = {}", 
            writeInterval, jobResultCache.size, HtrcConfig.cacheSize, 
            HtrcConfig.cacheJobs)

  // list of cached job folders that have to be removed after the cache index
  // is written to disk; the cache index is written out periodically; this
  // list contains cache entries that have been eliminated from
  // jobResultCache because of limited capacity
  var cachedJobsToDelete: List[String] = Nil

  val behavior: PartialFunction[Any,Unit] = {
    case m: CacheControllerMessage => 
      m match {
        case GetJobFromCache(js, token) =>
          log.debug("CACHE_CONTROLLER received GetJobFromCache({}, {})", js, 
                    token)
	  val currSender = sender

          // retrieve metadata of input worksets of the job submission
          // Future[List[(String, Option[WorksetMetadata])]]
          val collectionMetadataF =
            Future.sequence(js.collections map { collectionName =>
              RegistryHttpClient.collectionMetadata(collectionName, token) map {
                (collectionName, _)
              }  
            })

          // retrieve the algorithm XML file for the algorithm in the job
          // submission
          val algMetadataF =
            RegistryHttpClient.algorithmMetadata(js.algorithm, token)

          for {
            lsCollectionMetadata <- collectionMetadataF
            algMetadata <- algMetadataF
            jobKey <- JobResultCache.constructKey(js, algMetadata,
              lsCollectionMetadata, token)
          } {
            self ! CacheLookup(jobKey, algMetadata, lsCollectionMetadata,
              currSender)
          }

	case CacheLookup(optKey, algMetadata, lsCollectionMetadata, asker) =>
          log.debug("CACHE_CONTROLLER received CacheLookup({}, {})", optKey, 
                    asker)
          val cacheRes = optKey flatMap { key => jobResultCache.get(key) }
	  val result: Either[DataForJobRun, DataForCachedJob] = 
	    cacheRes map { cachedJobId => 
	      Right(DataForCachedJob(cachedJobId, algMetadata,
                lsCollectionMetadata))
	    } getOrElse { 
              val resKey = if (HtrcConfig.cacheJobs) optKey else None
	      Left(DataForJobRun(resKey, algMetadata, lsCollectionMetadata)) 
	    }
	  asker ! result

	case GetDataForJobRun(js, token) =>
	  val currSender = sender
	  log.debug("CACHE_CONTROLLER received GetDataForJobRun({}, {})", js, 
                    token)

          // retrieve metadata of input worksets of the job submission
          // Future[List[(String, Option[WorksetMetadata])]]
          val collectionMetadataF =
            Future.sequence(js.collections map { collectionName =>
              RegistryHttpClient.collectionMetadata(collectionName, token) map {
                (collectionName, _)
              }  
            })

          // retrieve the algorithm XML file for the algorithm in the job
          // submission
          val algMetadataF =
            RegistryHttpClient.algorithmMetadata(js.algorithm, token)

          for {
            lsCollectionMetadata <- collectionMetadataF
            algMetadata <- algMetadataF
          } {
            // if HtrcConfig.cacheJobs is set to false, then there is no
            // need for the key
	    if (!HtrcConfig.cacheJobs)
              currSender ! DataForJobRun(None, algMetadata, lsCollectionMetadata)
            else {
              JobResultCache.constructKey(js, algMetadata,
                lsCollectionMetadata, token) map { jobKey =>
                currSender ! DataForJobRun(jobKey, algMetadata,
                  lsCollectionMetadata)
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
	      addJobToCache(key, cachedJobId)
              // jobResultCache.put(key, cachedJobId)
            }
	  } else {
            log.debug("CACHE_CONTROLLER: cacheJobs is false, " + 
                      "or cache already contains key, or " + 
                      "job to be cached has missing job results; " + 
                      "not caching {} job {}",
                      jobStatus.algorithm, jobStatus.id)
	  }

	case WriteCacheToFile =>
          // log.debug("CACHE_CONTROLLER received WriteCacheToFile")
	  jobResultCache.writeCacheToFileIfNeeded(HtrcConfig.cacheFilePath)
          // remove the folders of any cached jobs that are no longer in the
          // just-written cache index
          removeCachedJobs()
          system.scheduler.scheduleOnce(writeInterval seconds, self, 
                                        WriteCacheToFile)

      }
  }

  val unknown: PartialFunction[Any,Unit] = {
    case m =>
      log.error("Cache controller received unhandled message")
  }

  def addJobToCache(key: String, cachedJobId: String): Unit = {
    jobResultCache.put(key, cachedJobId) foreach { 
      case(removedKey, removedJobId) => 
	// add the removed job id to cachedJobsToDelete, so that the job
	// folder is removed on the next write of jobResultCache; if the job
	// folder is removed immediately, and the agent is stopped before
	// jobResultCache is written to disk, upon agent startup
	// jobResultCache will contain a job id that no longer exists in the
	// folder containing cached jobs
        cachedJobsToDelete = removedJobId :: cachedJobsToDelete
        log.debug("CACHE_CONTROLLER: addJobToCache, cachedJobsToDelete = {}", 
		  cachedJobsToDelete)
    }
  }

  // remove job folders in the cachedJobsToDelete list
  def removeCachedJobs(): Unit = {
    val cacheIndexJobIds = jobResultCache.values
    for (cachedJobId <- cachedJobsToDelete
	 // ensure that these jobs are not in jobResultCache
         if (! cacheIndexJobIds.contains(cachedJobId))) {
      // val sep = File.separator
      val cachedJobDir = HtrcConfig.cachedJobsDir + sep + cachedJobId
      deleteDir(cachedJobDir)
    }
    cachedJobsToDelete = Nil
  }

  // remove orphaned cached job folders, i.e., the cached jobs that are not
  // present in the persistent cache index; this function is expected to be
  // called at startup, after jobResultCache has been initialized; it handles
  // scenarios where jobs are added to jobResultCache, after which the agent
  // shuts down, before jobResultCache, containing the newly added jobs, is
  // written to disk
  def cleanUpCachedJobsFolder(): Unit = {
    val cachedJobDirs = 
      (new File(HtrcConfig.cachedJobsDir)).list(DirectoryFileFilter.DIRECTORY)
    val cacheIndexJobIds = jobResultCache.values
    for (cachedJobId <- cachedJobDirs
	 // ensure that these jobs are not in jobResultCache
         if (! cacheIndexJobIds.contains(cachedJobId))) {
      val orphanedCachedJobDir = HtrcConfig.cachedJobsDir + sep + cachedJobId
      deleteDir(orphanedCachedJobDir)
    }
  }

  // returns true if all expected job results (specified in the algorithm
  // metadata) exist in the job result folder, false otherwise
  def allJobResultsAvailable(jobStatus: Finished): Boolean = {
    // val sep = File.separator
    val jobResultSubdir = HtrcConfig.resultDir + sep + jobStatus.jobResultLoc +
                          sep + HtrcConfig.systemVariables("output_dir")
    jobStatus.inputs.system.nonOptionalResults forall { res => 
      // log.debug("CACHE_CONTROLLER: looking for {}", jobResultSubdir + sep + res)
      HtrcUtils.fileExists(jobResultSubdir + sep + res)
    }
  }

  // create a new job folder in HtrcConfig.cachedJobsDir which is a copy of
  // the job result folder of the given job, and return
  // Some(newJobFolderName); return None if the copy is unsuccessful
  def copyJobToCacheDir(jobStatus: Finished): Option[String] = {
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
	log.error("CACHE_CONTROLLER: exception in copyJobToCacheDir {}", e)
        None
    }
  }

  def deleteDir(dir: String): Boolean = {
    try {
      FileUtils.deleteDirectory(new File(dir))
      log.debug("CACHE_CONTROLLER: deleted cached job folder {}", dir)
      true
    } catch {
      case e: Exception => 
	log.error("CACHE_CONTROLLER: exception in deleteDir({}), {}", dir, e)
	false
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

// response for message GetDataForJobRun; algMetadata and
// lsCollectionMetadata contain details needed to launch the job;
// lsCollectionMetadata is a list of tuples of the form (<worksetName>,
// Option[WorksetMetadata]); the jobResultCacheKey is used if the results of
// the job after execution are to be cached
case class DataForJobRun(jobResultCacheKey: Option[String], 
  algMetadata: JobProperties,
  lsCollectionMetadata: List[(String, Option[WorksetMetadata])])

// response for message GetJobFromCache if job is found in the cache;
// algMetadata is required to construct the final status of the cached job
// including the list of job results; lsCollectionMetadata is included in
// case it is needed at a later point
case class DataForCachedJob(cachedJobId: String, algMetadata: JobProperties,
  lsCollectionMetadata: List[(String, Option[WorksetMetadata])])
