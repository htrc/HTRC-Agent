
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

class JobResultCache(val maxEntries: Int = 1000) {

}

object JobResultCache {
  def constructKey(js: JobSubmission, algMetadata: JobProperties, token: String): Future[Option[String]] = {
    // key associated with a job = (algName, algVersion, algXMLTimestamp, 
    //                              params, collectionParamTimestamps)
    // params is a list of the form param1=<value>, param2=<value>, ...
    // collectionParamTimestamps is a list containing job params that are
    // collections and the timestamps of those collections,
    // paramiTs=<timestamp>, paramjTs=<timestamp>, ...
    // val keyF = "(%s, %s, %s, %s, %s)"
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
              collectionName + "=" + collectionTimestamp
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
          (collectionTimestampList map { _.get }).mkString(sep)
        algXMLTsOpt map { algXMLTimestamp =>
          keyF.format(algName, algVersion, algXMLTimestamp, params, 
                      collectionTsString) 
	}
      }
    }
  }
}
