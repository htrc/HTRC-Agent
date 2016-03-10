
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

// A cache that allows for storage and lookup of job results.

import akka.event.LoggingAdapter
import scala.io.Source
import java.io.File
import java.io.PrintWriter
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.XML
import com.twitter.storehaus.cache._
import java.io._
import akka.event.Logging

class JobResultCache(val maxEntries: Int = 1000) {
  implicit val system = HtrcSystem.system
  var writeNecessary = true
  // import system.dispatcher
  val log = Logging(system, "job-result-cache")

  val lruCache = MutableLRUCache[String, String](maxEntries)
  readCacheFromFile(HtrcConfig.cacheFilePath)

  def contains(key: String): Boolean = {
    lruCache.contains(key)
  }

  def get(key: String): Option[String] = {
    lruCache.hit(key)
  }

  def put(key: String, jobId: String): Unit = {
    lruCache += (key, jobId)
    writeNecessary = true
  }

  def readCacheFromFile(cacheFilePath: String): Unit = {
    try {
      val cacheIndexXml = XML.loadFile(cacheFilePath)

      (cacheIndexXml \\ "cachedJob") map
        (n => ((n \ "key").text, (n \ "jobLocation").text)) foreach
      { case (cacheKey, jobLoc) => lruCache += (cacheKey, jobLoc) }
    } catch {
      case e: Exception => 
        log.debug("JOB_RESULT_CACHE: exception while reading cache from file; {}",
                  e)
    }
    writeCacheToLog
  }

  def writeCacheToFileIfNeeded(): Unit = {
    if (writeNecessary) {
      writeNecessary = false
      writeCacheToFile(HtrcConfig.cacheFilePath)
      // prettyPrintCacheToFile(HtrcConfig.readableCacheFilePath)
    }
  }

  def writeCacheToFile(cacheFilePath: String): Unit = {
    val iter = lruCache.iterator map
      { case (cacheKey, jobLoc) => 
        <cachedJob>
          <key>{cacheKey}</key>
          <jobLocation>{jobLoc}</jobLocation>
        </cachedJob> }
    val cacheIndexXml = <cacheIndex>{iter}</cacheIndex>

    XML.save(cacheFilePath, cacheIndexXml)
  }

  def prettyPrintCacheToFile(cacheFilePath: String): Unit = {
    val iter = lruCache.iterator map
      { case (cacheKey, jobLoc) => 
        <cachedJob>
          <key>{cacheKey}</key>
          <jobLocation>{jobLoc}</jobLocation>
        </cachedJob> }
    val cacheIndexXml = <cacheIndex>{iter}</cacheIndex>

    // 500 characters wide, 2 character indentation
    val prettyPrinter = new scala.xml.PrettyPrinter(500, 2)
    val readableXml = prettyPrinter.format(cacheIndexXml)

    val bw = new BufferedWriter(new FileWriter(new File(cacheFilePath)))
    bw.write(readableXml)
    bw.close()
  }

  def size: Int = {
    lruCache.iterator.size
  }

  def writeCacheToLog(): Unit = {
    val cacheIndexStr = lruCache.iterator mkString ", "
    log.debug("CACHE_INDEX: size = {}, contents = [{}]", 
              lruCache.iterator.size, cacheIndexStr)
  }
}

object JobResultCache {
  def constructKey(js: JobSubmission, algMetadata: JobProperties, token: String): Future[Option[String]] = {
    // key associated with a job = (algName, algVersion, algXMLTimestamp, 
    //                              params, collectionParamTimestamps)
    // params is a list of the form param1=<value>, param2=<value>, ...
    // collectionParamTimestamps is a list containing job params that are
    // collections and the timestamps of those collections,
    // paramiTs=<timestamp>, paramjTs=<timestamp>, ...
    val keyF = "(%s, %s, %s, %s, %s)"

    val algName = js.algorithm
    val algVersion = algMetadata.algVersion

    val sep = ", "
    val params = js.userInputs map { 
      case(paramName, paramValue) => paramName + "=" + paramValue 
    } mkString sep

    val algXMLTimestampF = 
      RegistryHttpClient.algorithmXMLTimestamp(algName, token)

    val collectionTimestampsF = 
      Future.sequence(js.collections map { 
	collectionName => 
          RegistryHttpClient.collectionTimestamp(collectionName, token) map { 
            _ map { collectionTimestamp =>
              collectionName + "Ts=" + collectionTimestamp
            }
          }
      })

    for {
      algXMLTsOpt <- algXMLTimestampF
      collectionTimestampList <- collectionTimestampsF
    } yield {
      if (collectionTimestampList contains None) 
        None
      else {
        val collectionTsString = 
          collectionTimestampList.flatten mkString sep
        algXMLTsOpt map { algXMLTimestamp =>
          keyF.format(algName, algVersion, algXMLTimestamp, params, 
                      collectionTsString) 
	}
      }
    }
  }
}
