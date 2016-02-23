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

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import spray.httpx.encoding._
import spray.routing.directives._
import CachingDirectives._
import spray.util._
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Future
import akka.event.Logging
import scala.xml._

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class AgentServiceActor extends Actor with AgentService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(agentRoute)

}

// this trait defines our service behavior independently from the service actor
trait AgentService extends HttpService {
  implicit def executionContext = actorRefFactory.dispatcher

  // Implicit val timeout = Timeout(5 seconds)
  implicit val timeout = Timeout(30 seconds)

  // logging setup
  import HtrcLogSources._
  val log = Logging(HtrcSystem.system.eventStream, "htrc.system")
  log.debug("AgentService logger initialized")

  // to dispatch messages we need our global store of agents
  val agents = HtrcAgents

  // for building new agents
  val agentBuilder = HtrcSystem.agentBuilder

  // our behavior is always to lookup a user, then do something
  // depending on whether or not they exist yet

  // NodeSeq should turn into some sort of HtrcResponse type with a marshaller
  def dispatch(user: HtrcUser)(body: => AgentMessage): Future[NodeSeq] = {
    val agent = agents.lookupAgent(user)
    if(agent == None) {
      (agentBuilder ? BuildAgent(user, body)).mapTo[NodeSeq]
    } else {
      (agent.get ? body).mapTo[NodeSeq] 
    }
  }

  def token(t: String): String = t.split(' ')(1)

  // the agent api calls
  val agentRoute =
    headerValueByName("Authorization") { tok =>
    headerValueByName("htrc-remote-address") { ip =>
    headerValueByName("htrc-request-id") { requestId =>
    headerValueByName("htrc-remote-user") { rawUser =>
      val userName = rawUser.split('@')(0)
      log.debug("userName = " + userName)

      pathPrefix("algorithm") {
        pathPrefix("run") {    
          (post | put) {
            parameters('usecache.as[Boolean] ? HtrcConfig.useCache) { useCache =>
              entity(as[NodeSeq]) { userInput =>
                val algorithm = userInput \ "algorithm" text
                val token = tok.split(' ')(1)
                // val inputProps =
                //   RegistryHttpClient.algorithmMetadata(algorithm, token)

                log.debug("AGENT_SERVICE /algorithm/run: useCache = {}",
                          useCache)

		val js = JobSubmission(userInput, userName)
		val dataForJobRunF = (HtrcSystem.cacheController ? 
                  GetDataForJobRun(js, token)).mapTo[DataForJobRun]

                complete(
                  dataForJobRunF map { dataForJobRun =>
                    log.debug("AGENT_SERVICE /algorithm/run: cacheKey = {}",
                              dataForJobRun.jobResultCacheKey)
                   
                    val msg = 
                      RunAlgorithm(JobInputs(js, dataForJobRun.algMetadata,
                                             token, requestId, ip))
                    dispatch(HtrcUser(userName)) { msg }
                  }
                )
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
                       { AllJobStatuses(token(tok)) })
            }
          }
        } ~
        pathPrefix("active") {
          path("status") {
            complete(dispatch(HtrcUser(userName)) 
                     { ActiveJobStatuses })
          }
        } ~
        pathPrefix("saved") {
          path("status") {
            complete(dispatch(HtrcUser(userName)) 
                     { SavedJobStatuses(token(tok)) })
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
                       {  SaveJob(JobId(id), token(tok)) })
            }
          } ~
          path("delete") {
            delete {
              complete(dispatch(HtrcUser(userName)) 
                     { DeleteJob(JobId(id), token(tok)) })
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
                  respondWithStatus(StatusCodes.BadRequest) {
                    complete(err)
                  }
                }
                else 
                  complete(dispatch(HtrcUser(usrName)) 
                           { UpdateJobStatus(JobId(id), token(tok), 
                                             userInput) })
  	      }
  	    }
  	  }
        }
      }
    // ~
    // pathPrefix("") { 
    //   complete("Path is not a valid API query.")
    // }
      }      
    }                                      
  }
}}
