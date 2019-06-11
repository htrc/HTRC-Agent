
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

// An example actor that uses the Spray asynchronous http client

import scala.concurrent.Future
import akka.actor._
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.{ ActorSystem, Props, Actor }
import akka.pattern.ask
import akka.event.Logging
import akka.event.slf4j.Logger
import scala.util.{Success, Failure}
import scala.xml._
import HtrcConfig._
import java.io.File
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import com.github.tototoshi.csv._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.HttpCharsets

import scala.xml._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.RawHeader
import akka.util.ByteString
import akka.stream.scaladsl.{Source, Sink}
import scala.util.Try

object RegistryHttpClient {

  implicit val system = HtrcSystem.system
  implicit val executor = system.dispatcher
  implicit val materializer = HtrcSystem.materializer

  implicit val regTimeout = Timeout(5 seconds)
  // import system.dispatcher
  // override val log = Logging(system, "registry-http-client")
  val log = Logging(system, "registry-http-client")
  val auditLog = Logger("audit")

  val registryUrl = HtrcConfig.registryUrl
  val poolClientFlow =
    Http().cachedHostConnectionPoolHttps[String](HtrcConfig.registryHost)

  def query(queryStr: String, method: HttpMethod, token: String,
    acceptContentType: String = "application/xml",
    body: Option[NodeSeq] = None): Future[ByteString] = {
    val request = registryUrl + queryStr

    val headers = List(RawHeader("Accept", acceptContentType),
      RawHeader("Authorization", "Bearer " + token))

    val httpRequest =
      body map { b =>
        HttpRequest(method = method, uri = request, headers = headers,
          // entity = HttpEntity(ContentType(`application/xml`, () => HttpCharsets.`UTF-8`),
          entity = HttpEntity(MediaTypes.`application/xml`.withCharset(HttpCharsets.`UTF-8`),
            b.toString))
      } getOrElse {
        HttpRequest(method = method, uri = request, headers = headers)
      }

    val responseFuture = Http().singleRequest(httpRequest)

    // onFailure is deprecated
    responseFuture.onComplete {
      case Failure(e) =>
        // log error
        log.error("REGISTRY_QUERY error: request {}, method {}, exception {}", request, method, e)
      case Success(res) => // do nothing
    }

    // any exceptions in singleRequest are propagated through the returned
    // Future
    responseFuture flatMap { response =>
      if (response.status.isSuccess) {
        response.entity.dataBytes.runFold(ByteString.empty) {
          case (acc, b) => acc ++ b
        }
      } else {
        log.error("REGISTRY_QUERY: error in request {}, method {}, response status code {}", request, method, response.status.value)
        response.discardEntityBytes()
        throw new RuntimeException("Error in request " + request + ", " +
          method + ", response code " + response.status.value)
      }
    }
  }

  // Specific Agent Queries

  // retrieve the list of volumes in a workset, used in a given job
  // submission, and write the same to file
  def collectionData(rawName: String, inputs: JobInputs, dest: String): Future[Boolean] = {
    // audit log analyzer output
    // type collection_name request_id ip token job_id job_name algorithm
    val fstr = "REGISTRY_FETCH_COLLECTION\t%s\t%s\t%s\t%s\t%s\t%s".format(
             rawName, inputs.requestId, inputs.ip, inputs.token,
             inputs.name, inputs.algorithm)
    auditLog.info(fstr)

    val name = rawName.split('@')(0)
    val author = rawName.split('@')(1)

    // url encode the workset name; space is encoded as '+', but should be "%20"
    // when part of the url
    val encName = java.net.URLEncoder.encode(name, "utf-8").replace("+", "%20")

    val q =
      if (HtrcConfig.requiresWorksetWithHeader(inputs))
        // use new REST call to deal with worksets that may contain class
        // labels and other metadata apart from volume ids, and returns a
        // list with a "volume_id, class, ..." header
        query("worksets/"+encName+"/volumes?author="+author, GET,
          inputs.token, "text/csv")
      else
        // otherwise, use old REST call to get just a list of volume ids,
        // without any header row; required for
        // Extracted_Features_Download_Helper
        query("worksets/"+encName+"/volumes.txt?author="+author, GET,
          inputs.token)

    q transform {
      case Success(byteStr) =>
        Try { writeFileWithEmptyFieldSubst(byteStr.toArray, dest) }
      case Failure(e) => Try { false }
    }
  }

  // retrieve the algorithm XML file, and return a JobProperties object
  // constructed using the contents of this file
  def algorithmMetadata(name: String, token: String): Future[JobProperties] = {
    val q = query("files/algorithmfolder/"+name+".xml?public=true", GET, token)
    q map { byteStr =>
      try {
        val algMetadata = XML.loadString(byteStr.utf8String)
        JobProperties(algMetadata)
      } catch {
        case e: Exception =>
          val req = "files/algorithmfolder/" + name + ".xml"
          log.error("REGISTRY_QUERY: error reading result of {} request for {}, exception {}", GET, req, e)
          throw new RuntimeException("Error reading result of request for " +
            req + ": " + e)
      }
    }
  }

  // obtain the time of last modification of the specified algorithm XML file
  // returns Some(timestamp) if successful, None otherwise
  def algorithmXMLTimestamp(name: String, token: String): Future[Option[String]] = {
    val q = query("files/algorithmfolder/"+name+".xml?public=true", OPTIONS, 
                  token)

    q transform {
      case Success(byteStr) =>
        try {
          val algXMLMetadata = XML.loadString(byteStr.utf8String)
          Try { Some(algXMLMetadata \ "lastModified" text) }
        } catch {
          case e: Exception =>
            val req = "files/algorithmfolder/" + name + ".xml"
            log.error("REGISTRY_QUERY: error reading result of {} request for {}, exception {}", OPTIONS, req, e)
           Try { None }
        }

      case Failure(e) =>
        Try { None }
    }  
  }

  // retrieves the metadata for a collection, and returns
  // Some(WorksetMetadata) if successful, None otherwise 
  def collectionMetadata(name: String, token: String): Future[Option[WorksetMetadata]] = {
    val title = name.split('@')(0)
    val author = name.split('@')(1)

    // url encode the workset title; space is encoded as '+', but should be
    // "%20" when part of the url
    val encTitle = java.net.URLEncoder.encode(title, "utf-8").replace("+", "%20")

    val worksetQuery="worksets/"+encTitle+"/metadata?author="+author
    val q = query(worksetQuery, GET, token)

    q transform {
      case Success(byteStr) =>
        try {
          val metadata = XML.loadString(byteStr.utf8String)
          Try { Some(WorksetMetadata(metadata)) }
        } catch {
          case e: Exception =>
            log.error("REGISTRY_QUERY: error reading result of {} request for {}, exception {}", GET, worksetQuery, e)
           Try { None }
        }

      case Failure(e) =>
        Try { None }
    }
  }

  def fileDownload(name: String, inputs: JobInputs, dest: String): Future[Boolean] = {
    
    // audit log analyzer output
    // type collection_name request_id ip token job_id job_name algorithm
    val fstr = "REGISTRY_FETCH_FILE\t%s\t%s\t%s\t%s\t%s\t%s".format(
             name, inputs.requestId, inputs.ip, inputs.token,
             inputs.name, inputs.algorithm)
    auditLog.info(fstr)

    val q = query("files/"+name+"?public=true", GET, inputs.token)

    q transform {
      case Success(byteStr) =>
        Try { writeFile(byteStr.toArray, dest) }
      case Failure(e) => Try { false }
    }
  }

  // Saving and loading saved job information
  
  val savedJobLocation = HtrcConfig.savedJobLocation

  def listSavedJobs(token: String): Future[List[String]] = {
    // val start = System.nanoTime
    val jobsResult: Future[ByteString] =
      query("files/"+savedJobLocation, OPTIONS, token)

    jobsResult map { jobsListByteString => 
      // val end = System.nanoTime
      // val timeTaken = (end - start)/1e9d
      // log.debug("REGISTRY_QUERY: Time taken to get list of saved jobs: {} seconds", timeTaken)

      try { 
        val jobsList = XML.loadString(jobsListByteString.utf8String)
        (jobsList \ "entries" \ "entry") map { entry =>
          (entry \ "name").text
        } toList
      } catch {
        case e: Exception =>
          log.error("REGISTRY_QUERY: error reading result of {} request for files/ {}, exception {}", OPTIONS, savedJobLocation, e)
          throw new RuntimeException("Error reading result of request for files/" +
            savedJobLocation + ": " + e)
      }
    }
  }

  // downloadSavedJobs uses the Akka HTTP host-level client API in a
  // streaming fashion
  def downloadSavedJobs(names: List[String], token: String):
      Future[Seq[SavedHtrcJob]] = {
    // use streaming to ensure that requests do not overflow any buffers,
    // leading to "BufferOverflowException: Exceeded configured
    // max-open-requests value ... " errors; back-pressure is exerted on the
    // stage supplying requests if the requests cannot be processed quickly
    // enough
    Source(names).map { job =>
      createSavedJobRequest(job, token)
    }
      .via(poolClientFlow)
      .mapAsync(1) { // the parallelism factor does not matter; mapAsync
                     // ensures that the next stage of the stream receives
                     // String objects, as opposed to Future[String] in the
                     // case of map

      // it is necessary to consume the response entities in the stream,
      // otherwise requests might be queued indefinitely
      case (Success(response), jobId) =>
        if (response.status.isSuccess) {
          response.entity.dataBytes.runFold(ByteString.empty) {
            case (acc, b) => acc ++ b
          } map { byteString => (jobId, byteString.utf8String) }
        } else {
          log.error("REGISTRY_DOWNLOAD_SAVED_JOBS: error in request for file for job {}, response code {}", jobId, response.status.value)
          response.discardEntityBytes()
          Future { (jobId, "") }
        }
      case (Failure(e), jobId) =>
        log.error("REGISTRY_DOWNLOAD_SAVED_JOBS: request for file for job {} failed with exception {}", jobId, e)
        Future { (jobId, "") }
    }
    .map { case (jobId, jobXML) =>
      try {
        val raw = XML.loadString(jobXML)
        Some(SavedHtrcJob(raw))
      } catch {
        // ignore files in files/<savedJobLocation> that are not in the
        // expected format
        case e: Exception =>
          log.debug("REGISTRY_DOWNLOAD_SAVED_JOBS warning: unexpected error " +
            "in reading file for job {}, exception {}", jobId, e)
          None
      }
    }
    .filter { _.isDefined } // remove all None elements to return only
                            // successfully downloaded jobs
    .map { _.get } // Some(SavedHtrcJob) to SavedHtrcJob
    .runWith(Sink.seq)
  }

  def createSavedJobRequest(jobId: String, token: String):
      (HttpRequest, String) = {
    val headers = List(RawHeader("Accept", "application/xml"),
      RawHeader("Authorization", "Bearer " + token))

    HttpRequest(method = GET, uri = registryUrl + "files/" +
      savedJobLocation + "/" + jobId, headers = headers) -> jobId
  }

  def saveJob(status: JobComplete, id: String, 
              token: String): Future[Boolean] = {
    // use the new registry extension API that requires the username
    val saveJobQuery = "files/" + savedJobLocation + "/" + id + 
                       "?user=" + status.submitter
    val q = query(saveJobQuery, PUT, token, body = Some(status.saveXml))

    q transform {
      case Success(byteStr) => Try { true }
      case Failure(e) => Try { false }
    }
  }

  def deleteJob(id: String, token: String): Future[Boolean] = {
    val q = query("files/"+savedJobLocation+"/"+id, DELETE, token)
    q transform {
      case Success(byteStr) => Try { true }
      case Failure(e) => Try { false }
    }
  }

  def now[T](f: Future[T]): T = scala.concurrent.Await.result(f, 5 seconds)

  def writeFile(bytes: Array[Byte], dest: String): Boolean = {
    try {
      val out = new java.io.FileOutputStream(dest)
      var res = true
      try {
        out.write(bytes)
      } catch {
        case e: Exception =>
          log.error("REGISTRY_QUERY: Exception in writing to {}: {}",
            dest, e)
          res = false
      } finally {
        out.close()
      }
      res
    } catch {
      case e: Exception =>
        log.error("REGISTRY_QUERY: Exception in writing to {}: {}",
          dest, e)
        false
    }
  }

  // csv data for worksets may contain empty fields; these are not handled by
  // Meandre; replace empty fields with a dummy value; call writeFile if
  // substitution of empty fields is not required
  def writeFileWithEmptyFieldSubst(bytes: Array[Byte], dest: String): Boolean = {
    val byteRdr = new InputStreamReader(new ByteArrayInputStream(bytes))
    val reader = CSVReader.open(byteRdr)

    var res = true
    try {
      val writer = CSVWriter.open(new File(dest))
 
      try {
        reader foreach { row =>
          writer.writeRow(row.map(x => (if (x == "") "HTRC_DUMMY_VAL" else x)))
        }
      } catch {
        case e: Exception => 
          log.error("REGISTRY_QUERY: exception in writing to {}: {}",
            dest, e)
          res = false
      } finally {
        writer.close()
      }
    } catch {
      case e: Exception =>
        log.error("REGISTRY_QUERY: exception in writing to {}: {}",
          dest, e)
        res = false
    } finally {
      byteRdr.close()
      reader.close()
    }
    res
  }

}
