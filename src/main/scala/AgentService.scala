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

  // test our http client
  // HttpClientTest.exampleOne()

  // to dispatch messages we need our global store of agents
  val agents = HtrcAgents

  // for building new agents
  val agentBuilder = HtrcSystem.agentBuilder

  // for development just assume a username
  val user = new HtrcUser("drhtrc")

  // our behavior is always to lookup a user, then do something
  // depending on whether or not they exist yet

  // the String response type is a huge hack! need to select something real
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
    pathPrefix("agent") {
      pathPrefix("algorithm") {
        pathPrefix("run") {
          (get | post | put) {
            complete(dispatch(user) { RunAlgorithm("Foo", SampleXmlInputs.wcInputs) })
          }
        }
      } ~ 
      pathPrefix("job") {
        pathPrefix("all") {
          pathPrefix("status") {
            complete(dispatch(user) { AllJobStatuses })
          }
        } ~
        pathPrefix("active") {
          pathPrefix("status") {
            complete(dispatch(user) { ActiveJobStatuses })
          }
        } ~
        pathPrefix("saved") {
          pathPrefix("status") {
            complete(dispatch(user) { SavedJobStatuses })
          }
        } ~
        pathPrefix(PathElement) { id =>
          pathPrefix("status") {
            complete(dispatch(user) { JobStatusRequest(JobId(id)) })
          } ~
          pathPrefix("save") {
            complete(dispatch(user) { SaveJob(JobId(id)) })
          } ~
          pathPrefix("delete") {
            complete(dispatch(user) { DeleteJob(JobId(id)) })
          } ~
          pathPrefix("result") {
            pathPrefix("stdout") {
              complete(dispatch(user) { JobOutputRequest(JobId(id), "stdout") })
            } ~
            pathPrefix("stderr") {
              complete(dispatch(user) { JobOutputRequest(JobId(id), "stderr") })
            } ~
            pathPrefix("directory") {
              complete(dispatch(user) { JobOutputRequest(JobId(id), "directory") })
            }
          }
        }
      }
    } ~
    pathPrefix("result") {
      getFromDirectory("agent_result_directories")
    } ~
    pathPrefix("") {
      complete("Path does not match API call")
    }
    
}

