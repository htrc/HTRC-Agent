
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
import spray.can.client.HttpClient
import spray.client.HttpConduit
import spray.io._
import spray.util._
import spray.http._
import HttpMethods._
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.{ ActorSystem, Props, Actor }
import HttpMethods._
import akka.pattern.ask
import HttpConduit._
import HttpClient._
import akka.event.Logging
import scala.util.{Success, Failure}
import scala.xml._
import HtrcConfig._
import MediaTypes._

object RegistryHttpClient {

  // the usual setup
  implicit val timeout = Timeout(5 seconds)
  implicit val system = HtrcSystem.system
  val log = Logging(system, "registry-http-client")

  // initialize the bridge to the IO layer used by Spray
  val ioBridge = IOExtension(system).ioBridge()

  // now anything we do can use the ioBridge to create connections
  // unknown : should I try and reuse an htpclient actor between queries?

  def query(query: String, method: HttpMethod, token: String, body: Option[NodeSeq] = None): Future[HttpResponse] = {

    // since we are using the registry I can just grab some info
    val root = registryHost
    val path = "/ExtensionAPI-"+registryVersion+"/services/"
    val port = registryPort

    // create a client to use
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

    // now we define a conduit, which is a use of the client
    val conduit = system.actorOf(
      props = Props(new HttpConduit(httpClient, root, port))
    )
    
    // the pipeline is exactly what happens with our request
    val pipeline: HttpRequest => Future[HttpResponse] = (
      addHeader("Accept", "application/xml")
      ~> addHeader("Authorization", "Bearer " + token)
      ~> sendReceive(conduit)
    )

    // how do we represent the content type of the xml?
    val contentType = `application/xml`

    // and now finally make a request
    val response = 
      if(body == None)
        pipeline(HttpRequest(method = method, uri = path + query)).mapTo[HttpResponse]
      else
        pipeline(HttpRequest(method = method, uri = path + query, 
                             entity = HttpBody(contentType, body.get.toString))).mapTo[HttpResponse]

    log.debug("REGISTRY_CLIENT_QUERY\tTOKEN: {}\tQUERY: {}",
             token, query)

    response

  }

  // these two functions primarily for debugging

  def printResponse(response: Future[HttpResponse]) {
    response onComplete {
      case Success(response) =>
        log.info(
          """|Response for GET request to random registry url:
          |status : {}
          |headers: {}
          |body   : {}""".stripMargin,
          response.status.value, response.headers.mkString("\n  ", "\n  ", ""), 
          response.entity.asString)
      case Failure(error) =>
        log.error(error.toString)
    }
  }

  def queryAndPrint(str: String, method: HttpMethod, token: String) {
    printResponse(query(str, method, token))
  }

  // Specific Agent Queries
  
  def collectionData(rawName: String, inputs: JobInputs, dest: String): Future[Boolean] = {
    // audit log analyzer output
    // type collection_name request_id ip token job_id job_name algorithm
    val fstr = "REGISTRY_FETCH_COLLECTION\t{}\t{}\t{}\t{}\t{}\t{}".format(
             rawName, inputs.requestId, inputs.ip, inputs.token,
             inputs.name, inputs.algorithm)
    log.info(fstr)

    val name = rawName.split('@')(0)
    val author = rawName.split('@')(1)
    val q = query("worksets/"+name+"/volumes.txt?author="+author, GET, inputs.token)
    q map { response =>
      writeFile(response.entity.buffer, dest)
      true
    }
  }

  def algorithmMetadata(name: String, token: String): Future[JobProperties] = {
    val q = query("files/algorithmfolder/"+name+".xml?public=true", GET, token)
    q map { response =>
      JobProperties(XML.loadString(response.entity.asString)) }
  }
    
  def fileDownload(name: String, inputs: JobInputs, dest: String): Future[Boolean] = {
    
    // audit log analyzer output
    // type collection_name request_id ip token job_id job_name algorithm
    val fstr = "REGISTRY_FETCH_FILE\t{}\t{}\t{}\t{}\t{}\t{}".format(
             name, inputs.requestId, inputs.ip, inputs.token,
             inputs.name, inputs.algorithm)
    log.info(fstr)

    val q = query("files/"+name+"?public=true", GET, inputs.token)
    q map { response =>
      val bytes = response.entity.buffer
      writeFile(bytes, dest) 
      true
    }
  }

  // Saving and loading saved job information
  
  val savedJobLocation = HtrcConfig.savedJobLocation

  def listSavedJobs(token: String): Future[List[String]] = {
    val q = query("files/"+savedJobLocation, OPTIONS, token)
    q map { response =>
      val raw = XML.loadString(response.entity.asString)
      (raw \ "entries" \ "entry") filter { entry =>
        ((entry \ "contentType").text != "collection")
      } map { entry => 
        (entry \ "name").text
      } toList
    }
  }    
    
  def downloadSavedJobs(names: List[String], token: String): Future[List[SavedHtrcJob]] = {
    val qs = names map { n => query("files/"+savedJobLocation+"/"+n, GET, token) }

    val fqs = Future.sequence(qs.toList)
    val res = fqs map { li => li.map { response =>
      val raw = XML.loadString(response.entity.asString)
      SavedHtrcJob(raw)
    }}
    res
  }

  def saveJob(status: Finished, id: String, token: String): Future[Boolean] = {
    val q = query("files/"+savedJobLocation+"/"+id, PUT, token, Some(status.saveXml))
    q map { response => true }
  }

  def deleteJob(id: String, token: String): Future[Boolean] = {
    val q = query("files/"+savedJobLocation+"/"+id, DELETE, token)
    q map { response => true }
  }
     
  def now[T](f: Future[T]): T = scala.concurrent.Await.result(f, 5 seconds)

  def writeFile(bytes: Array[Byte], dest: String) {
    val out = new java.io.FileOutputStream(dest)
    try {
      out.write(bytes)
    } finally {
      out.close()
    }
  }
    

}

