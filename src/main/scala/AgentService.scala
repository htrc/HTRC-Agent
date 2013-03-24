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

  implicit val timeout = Timeout(5 seconds)

  // logging setup
  import HtrcLogSources._
  val log = Logging(HtrcSystem.system.eventStream, "htrc.system")
  log.info("AgentService logger initialized")

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

  // the agent api calls
  val agentRoute =
    headerValueByName("Authorization") { tok =>
      headerValueByName("htrc-remote-user") { rawUser =>
        pathPrefix("agent") {      
          pathPrefix("algorithm") {
            pathPrefix("run") {
              get { ctx =>
                val wordcountUser = SampleXmlInputs.wordcountUser               
                val token = tok.split(' ')(1)
                val inputProps = 
                  RegistryHttpClient.algorithmMetadata("Simple_Deployable_Word_Count", token)
                ctx.complete(
                  inputProps map { in =>
                    RunAlgorithm("Foo", JobInputs(JobSubmission(wordcountUser), in, token))
                  } map { msg =>
                    dispatch(HtrcUser(rawUser, "0.0.0.0")) { msg }
                  }
                )                          
              }
            } ~          
            pathPrefix("run") {    
              (post | put) {
                entity(as[NodeSeq]) { userInput =>
                val algorithm = userInput \ "algorithm" text
                val token = tok.split(' ')(1)
                val inputProps =
                  RegistryHttpClient.algorithmMetadata(algorithm, token)

                  complete(
                    inputProps map { in =>
                      RunAlgorithm("Foo", JobInputs(JobSubmission(userInput), in, token))
                    } map { msg =>
                      dispatch(HtrcUser(rawUser, "0.0.0.0")) { msg }
                    }
                  )
                }
              }
            }   
          } ~ 
          pathPrefix("job") {
            pathPrefix("all") {
              pathPrefix("status") {
                complete(dispatch(HtrcUser(rawUser, "0.0.0.0")) 
                         { AllJobStatuses(tok) })
              }
            } ~
            pathPrefix("active") {
              pathPrefix("status") {
                complete(dispatch(HtrcUser(rawUser, "0.0.0.0")) 
                         { ActiveJobStatuses })
              }
            } ~
            pathPrefix("saved") {
              pathPrefix("status") {
                complete(dispatch(HtrcUser(rawUser, "0.0.0.0")) 
                         { SavedJobStatuses(tok) })
              }
            } ~
            pathPrefix(PathElement) { id =>
              pathPrefix("status") {
                complete(dispatch(HtrcUser(rawUser, "0.0.0.0")) 
                         { JobStatusRequest(JobId(id)) })
              } ~
            pathPrefix("save") {
              (put | post) {
                complete(dispatch(HtrcUser(rawUser, "0.0.0.0")) 
                         {  SaveJob(JobId(id), tok) })
              }
            } ~
            pathPrefix("delete") {
              delete {
                complete(dispatch(HtrcUser(rawUser, "0.0.0.0")) 
                       { DeleteJob(JobId(id), tok) })
              }
            } ~
            pathPrefix("result") {
              pathPrefix("stdout") {
                complete(dispatch(HtrcUser(rawUser, "0.0.0.0")) 
                         { JobOutputRequest(JobId(id), "stdout") })
            } ~
            pathPrefix("stderr") {
              complete(dispatch(HtrcUser(rawUser, "0.0.0.0")) 
                       { JobOutputRequest(JobId(id), "stderr") })
            } ~
            pathPrefix("directory") {
              complete(dispatch(HtrcUser(rawUser, "0.0.0.0")) 
                       { JobOutputRequest(JobId(id), "directory") })
            }
          }
        }
      }
    } ~
    pathPrefix("result") {
      getFromDirectory("agent_result_directories")
    } ~
    pathPrefix("") { 
      complete("Path is not a valid API query.")
    }
  }      
 }                                      
}


