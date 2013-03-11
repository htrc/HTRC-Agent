
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

object RegistryHttpClient {

  // the usual setup
  implicit val timeout = Timeout(5 seconds)
  implicit val system = HtrcSystem.system
  val log = Logging(system, "registry-http-client")

  // initialize the bridge to the IO layer used by Spray
  val ioBridge = IOExtension(system).ioBridge()

  // now anything we do can use the ioBridge to create connections
  // unknown : should I try and reuse an htpclient actor between queries?

  def query(query: String, method: HttpMethod, token: String): Future[HttpResponse] = {

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

    // and now finally make a request
    val response = pipeline(HttpRequest(method = method, uri = path + query)).mapTo[HttpResponse]

    log.info("query url: " + path + query)

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
  
  def collectionData(name: String, token: String, dest: String): Future[Boolean] = {
    val q = query("worksets/"+name+"/volumes.txt", GET, token)
    q map { response =>
      writeFile(response.entity.buffer, dest)
      log.info("wrote file: " + dest)
      true
    }
  }

  def algorithmMetadata(name: String, token: String): Future[JobProperties] = {
    val q = query("files/algorithmfolder/"+name+".xml", GET, token)
    q map { response =>
      JobProperties(XML.loadString(response.entity.asString)) }
  }
    
  def fileDownload(name: String, token: String, dest: String): Future[Boolean] = {
    val q = query("files/"+name, GET, token)
    q map { response =>
      val bytes = response.entity.buffer
      writeFile(bytes, dest) 
      log.info("wrote file: " + dest)
      true
    }
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

