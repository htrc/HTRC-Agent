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

import java.util.UUID
import akka.actor.Actor
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.{ExecutionContext, Future, Await}
import akka.event.Logging
import scala.xml._

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import scala.io.StdIn
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.event.LoggingAdapter

import scala.util.{Failure, Success}
import akka.util.ByteString
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import scala.util.{Success, Failure}
import java.io.InputStream
import java.io.FileInputStream
import java.security.{ SecureRandom, KeyStore }
import javax.net.ssl.{ SSLContext, TrustManagerFactory, KeyManagerFactory }
import akka.http.scaladsl.{ ConnectionContext, HttpsConnectionContext, Http }
import java.io.{ StringWriter, PrintWriter }

object HtrcJobsServer extends App with AgentService {
  override implicit val system = HtrcSystem.system
  override implicit val executor = system.dispatcher
  override implicit val materializer = HtrcSystem.materializer

  override val log = Logging(system, getClass)
  log.debug("HtrcJobsServer logger initialized")

  // jwtChecker is used for verifying and processing JWTs in incoming
  // requests
  override val jwtChecker = JwtChecker()

  // listening to http port
  // val serverBinding: Future[Http.ServerBinding] =
  //   Http().bindAndHandle(agentRoute, "0.0.0.0", 9000)

  try {
    val https = getHttpsConnectionContext()
    Http().setDefaultServerHttpContext(https)
    Http().bindAndHandle(agentRoute, "0.0.0.0", 8443, connectionContext = https)

    Await.result(system.whenTerminated, Duration.Inf)
  } catch {
    case e: Exception =>
      log.error("Exception in creating ConnectionContext: " + getStackTraceAsString(e))
      system.terminate()
  }

  /*
  serverBinding.onComplete {
    case Success(bound) =>
      println(s"Agent online at http://\${bound.localAddress.getHostString}:\${bound.localAddress.getPort}/")
    case Failure(e) =>
      Console.err.println(s"The agent could not start!")
      e.printStackTrace()
      system.terminate()
  }
   */ 

  def getHttpsConnectionContext(): HttpsConnectionContext = {
    val ks: KeyStore = KeyStore.getInstance("PKCS12")
    val keystore: InputStream = new FileInputStream(HtrcConfig.serverCert)
    val passwd = HtrcConfig.serverCertPasswd.toCharArray

    require(keystore != null, "Keystore required!")
    ks.load(keystore, passwd)

    val keyManagerFactory: KeyManagerFactory =
      KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, passwd)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers,
      new SecureRandom)
    ConnectionContext.https(sslContext)
  }

  def getStackTraceAsString(t: Throwable): String = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}

// this trait defines our service behavior independently from the service actor
trait AgentService {
  // implicit def executionContext = actorRefFactory.dispatcher

  implicit val system: ActorSystem
  implicit def executor: ExecutionContext
  implicit val materializer: Materializer

  val log: LoggingAdapter
  val jwtChecker: JwtChecker

  implicit val timeout = Timeout(30 seconds) // originally 5 seconds

  // logging setup
  // import HtrcLogSources._
  // val log = Logging(system.eventStream, "htrc.system")
  // log.debug("AgentService logger initialized")

  // for creating HtrcAgent actors for users, and getting existing ones
  val userActors = HtrcSystem.userActors

  // verifies the JWT, and if verified, provides a tuple, (JWT, htrcUser) to
  // the inner route
  def authenticate: Directive1[(String, String)]  = {
    headerValueByName("Authorization").flatMap { authHeader =>
      if (! authHeader.startsWith("Bearer ")) {
        complete(StatusCodes.Unauthorized -> "Invalid authorization header.")
      } else {
        val token = authHeader.split(' ')(1)
          // obtain the suffix after "Bearer "
        jwtChecker.verify(token) match {
          case Success(decodedJWT) =>
            val htrcUser = decodedJWT.getClaim("sub").asString
            provide((token, htrcUser))

          case Failure(e) =>
            complete(StatusCodes.Unauthorized -> e.getMessage)
        }
      }
    }
  }

  // verifies the JWT, and if verified, provides a tuple,
  // (JWT, Map[String, String]), to the inner route; the 2nd element in the
  // tuple is of the form
  // (htrcClaim1 -> <value>, htrcClaim2 -> <value>, ...)
  def authenticateWithHtrcClaims: Directive1[(String, Map[String, String])]  = {
    headerValueByName("Authorization").flatMap { authHeader =>
      if (! authHeader.startsWith("Bearer ")) {
        complete(StatusCodes.Unauthorized -> "Invalid authorization header.")
      } else {
        val token = authHeader.split(' ')(1)
          // obtain the suffix after "Bearer "
        jwtChecker.verify(token) match {
          case Success(decodedJWT) =>
            val htrcClaims = HtrcConfig.jwtClaimMappings.map { case(k, v) =>
              (v, decodedJWT.getClaim(k).asString)
            }
            provide((token, htrcClaims))

          case Failure(e) =>
            complete(StatusCodes.Unauthorized -> e.getMessage)
        }
      }
    }
  }

  // our behavior is always to lookup a user, then do something
  // depending on whether or not they exist yet

  // NodeSeq should turn into some sort of HtrcResponse type with a marshaller
  def dispatch(user: HtrcUser)(body: => AgentMessage): Future[NodeSeq] = {
    (userActors ? GetUserActor(user, body)).mapTo[NodeSeq]
  }

  // prints the incoming request including headers; useful for debugging
  // val agentRoute =
  //   requestInstance { request =>
  //     complete(s"Request method is ${request.method} and length is ${request.entity.data.length}; REQUEST=${request.toString}")
  //   }

  // the agent api calls
  val agentRoute =
    logRequest((req: HttpRequest) =>
      ("Request: " + req.method + ", " + req.uri + ", entity.contentType = " + req.entity.contentType)) {
    headerValueByName("Remote-Address") { ip =>
    authenticate { case (token, rawUser) => 
      val requestId = UUID.randomUUID.toString
      val userName = rawUser.split('@')(0)
      log.debug("userName = " + userName)

      pathPrefix("agent") {
      pathPrefix("algorithm") {
        pathPrefix("run") {    
          (post | put) {
            parameters('usecache.as[Boolean] ? HtrcConfig.useCache) { useCache =>
              entity(as[NodeSeq]) { userInput =>
                val algorithm = userInput \ "algorithm" text

                log.debug("AGENT_SERVICE /algorithm/run: useCache = {}",
                          useCache)

		val js = JobSubmission(userInput, userName)
		def runAlgorithm(dataForJobRun: DataForJobRun): Future[NodeSeq] = {
                  val msg = 
                    RunAlgorithm(JobInputs(js, dataForJobRun.algMetadata, 
                      dataForJobRun.jobResultCacheKey, token, requestId, ip,
                      dataForJobRun.lsCollectionMetadata))
                  dispatch(HtrcUser(userName)) { msg }
		}

		if (useCache) {
		  val cacheConRes = 
		  ((HtrcSystem.cacheController ? GetJobFromCache(js, token)).mapTo[Either[DataForJobRun, DataForCachedJob]]) 
		  complete( cacheConRes map { eith =>
		    eith match {
		      case Right(DataForCachedJob(cachedJobId, algMetadata, lsCollectionMetadata)) =>
                        log.debug("AGENT_SERVICE: received response " + 
				  "DataForCachedJob({})", cachedJobId)
                        val jobInputs = 
                          JobInputs(js, algMetadata, None, token, requestId, ip, lsCollectionMetadata)
			val msg = CreateJobFromCache(jobInputs, cachedJobId)
			dispatch(HtrcUser(userName)) { msg }
			
		      case Left(dataForJobRun) =>
                        log.debug("AGENT_SERVICE: received response " + 
				  "DataForJobRun(key = {})", 
				  dataForJobRun.jobResultCacheKey)
			runAlgorithm(dataForJobRun)
		    }
		  })
		} else {
		  val dataForJobRunF = 
                    (HtrcSystem.cacheController ? GetDataForJobRun(js, token)).mapTo[DataForJobRun]
		  complete(dataForJobRunF map { dataForJobRun => 
                    log.debug("AGENT_SERVICE: received response " + 
			      "DataForJobRun(key = {})", 
			      dataForJobRun.jobResultCacheKey)
                    runAlgorithm(dataForJobRun) 
                  })
		}
	      }
            }
          }
	}
      } ~
      pathPrefix("job") {
        pathPrefix("all") {
          path("status") {
            get {
              complete(dispatch(HtrcUser(userName)) 
                       { AllJobStatuses(token) })
            }
          }
        } ~
        pathPrefix("active") {
          path("status") {
            complete(dispatch(HtrcUser(userName)) 
                     { ActiveJobStatuses })
            /*
            complete(
              RegistryHttpClient.testQuery("https://emailvalidator.internal.htrc.indiana.edu", "/validate/test@atla.com", GET, token, "application/json"))
             */
            /*
            complete(
              RegistryHttpClient.testQuery("https://htc6.carbonate.uits.iu.edu/rights-api", "/filter?level=1|2", POST, token, "application/json", Some("""{"volumeIdsList":["mdp.39015080967915","mdp.39015080967600","mdp.39015080934287","mdp.39015080938338"]}""")))
             */
          }
        } ~
        pathPrefix("saved") {
          path("status") {
            complete(dispatch(HtrcUser(userName)) 
                     { SavedJobStatuses(token) })
          }
        } ~
        pathPrefix(Segment) { id =>
          path("status") {
            complete(dispatch(HtrcUser(userName)) 
                     { JobStatusRequest(JobId(id)) })
          } ~
          path("save") {
            (put | post) {
              complete(dispatch(HtrcUser(userName)) 
                       {  SaveJob(JobId(id), token) })
            }
          } ~
          path("delete") {
            delete {
              complete(dispatch(HtrcUser(userName)) 
                     { DeleteJob(JobId(id), token) })
            }
          } ~
          path("updatestatus") {
            (put | post) {
              entity(as[NodeSeq]) { userInput =>
                val usrName = (userInput \ "user" text)
                if (usrName == "") {
                  val err = "no user specified in updatestatus request"
                  log.debug("AGENT_SERVICE job/{}/updatestatus ERROR: {}, " + 
                            "unable to process updatestatus; userInput = {}", 
                            id, err, userInput)
                  complete(StatusCodes.BadRequest -> err)
                }
                else 
                  complete(dispatch(HtrcUser(usrName)) 
                           { UpdateJobStatus(JobId(id), token, userInput) })
  	      }
  	    }
  	  }
        }
      }
      }
      /*
      path("test") {
        /*
        complete(
          for {
            x <- testCollectionMetadata("creativity@leenatest", token)
            y <- testCollectionMetadata("Dickens Test@dkudeki", token)
            z <- testCollectionMetadata("ancestry@leenatest", token)
            a <- testCollectionMetadata("Action and Adventure@leenatest1", token)
          } yield { x + "\n" + y + "\n" + z + "\n" + a })
         */

        val a = "run_topicexplorer.sh"
        val b = "run_NamedEntityRecognizer.sh"
        val c = "foo"
        complete(
          for {
            x <- testFileDownload(userName, a, token, requestId, ip)
            y <- testFileDownload(userName, b, token, requestId, ip)
            z <- testFileDownload(userName, c, token, requestId, ip)
          } yield { s"$a: $x \n$b: $y \n$c: $z" })
      }
       */
    // ~
    // pathPrefix("") { 
    //   complete("Path is not a valid API query.")
    // }
      }
    }
  }

  def testCollectionData(userName: String, token: String, requestId: String,
    ip: String): Future[String] = {
    // InPho_Topic_Explorer
    val testJobXML =
      <job>
    <name>inpho-test</name>
    <username>leenatest</username>
    <algorithm>EF_Rsync_Script_Generator</algorithm>
    <parameters>
    <param value="creativity@leenatest" type="collection" name="input_collection"/>
    <param name="iter" type="integer" value="200"/>
    <param name="k" type="string" value="20 40 60 80"/>
    </parameters>
    </job>
    val js = JobSubmission(testJobXML, userName)

    val alg = <algorithm></algorithm>
    val jobProps = JobProperties(alg)

    val jobInputs = JobInputs(js, jobProps, None, token, requestId, ip)
    val dest = "/opt/docker/logs/temp"

    RegistryHttpClient.collectionData("creativity@leenatest", jobInputs, dest).map { b => b.toString }
  }

  def testAlgorithmMetadata(algName: String, token: String): Future[NodeSeq] = {
    RegistryHttpClient.algorithmMetadata(algName, token).map { jobProps =>
      jobProps.metadata
    }
  }

  def testAlgorithmXMLTimestamp(algName: String, token: String): Future[String] = {
    RegistryHttpClient.algorithmXMLTimestamp(algName, token).map {
      optTimestamp => optTimestamp.getOrElse("null")
    }
  }

  def testCollectionMetadata(workset: String, token: String): Future[String] = {
    RegistryHttpClient.collectionMetadata(workset, token).map { opt =>
      opt match {
        case Some(wksetMetadata) =>
          s"(${wksetMetadata.name}, ${wksetMetadata.author}, ${wksetMetadata.volumeCount})"
        case None =>
          "null"
      }
    }
  }

  def testFileDownload(userName: String, fileName: String, token: String, requestId: String,
    ip: String): Future[String] = {
    // InPho_Topic_Explorer
    val testJobXML =
      <job>
    <name>inpho-test</name>
    <username>leenatest</username>
    <algorithm>EF_Rsync_Script_Generator</algorithm>
    <parameters>
    <param value="creativity@leenatest" type="collection" name="input_collection"/>
    <param name="iter" type="integer" value="200"/>
    <param name="k" type="string" value="20 40 60 80"/>
    </parameters>
    </job>
    val js = JobSubmission(testJobXML, userName)

    val alg = <algorithm></algorithm>
    val jobProps = JobProperties(alg)

    val jobInputs = JobInputs(js, jobProps, None, token, requestId, ip)
    val dest = "/opt/docker/logs/" + fileName

    RegistryHttpClient.fileDownload("dependencies/" + fileName, jobInputs, dest).map { b => b.toString }
  }

}
