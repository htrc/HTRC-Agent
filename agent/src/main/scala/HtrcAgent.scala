
package htrcagent

import httpbridge._

import akka.actor.{ Props, Actor, ActorRef }
import akka.util.Timeout
import akka.util.duration._
import akka.actor.Actor._
import scala.collection.mutable.HashMap
import akka.pattern.{ ask, pipe }
import akka.dispatch.{ Promise, Future }
import scala.xml._
import java.util.UUID

class HtrcAgent(token: Oauth2Token) extends Actor {

  import context._

  // timeout for ? (ask)
  implicit val timeout = Timeout(5 seconds)

  val algorithms = new HashMap[String,Future[ActorRef]]
  val username = token.username

  def registry: ActorRef = actorFor("/user/registryActor")

  def receive = {

    case DownloadCollection(collectionName) =>
      val f = (registry ? RegistryDownloadCollection(collectionName, username))
      f pipeTo sender

    case UploadCollection(data) =>
      // don't do much other than call the registry's upload call
      // return will be <collection>{collection_path}</collection>
      val f = (registry ? RegistryUploadCollection(data, username))
      f pipeTo sender

    case ModifyCollection(data) =>
      val f = (registry ? RegistryModifyCollection(data, username))
      f pipeTo sender

    case ListAvailibleAlgorithms => 
      val f = (registry ? RegistryListAvailibleAlgorithms(username)) 
      f pipeTo sender

    case AlgorithmDetails(algorithmName) =>
      val f = (registry ? RegistryAlgorithmDetails(username, algorithmName))
      f pipeTo sender

    case ListAvailibleCollections => 
      val f = (registry ? RegistryListAvailibleCollections(username))
      f pipeTo sender
      
    case RunAlgorithm(algorithmName, userProperties) => 
      // get an algId
      val algId = newAlgId
      // get a compute child
      val c = system.actorOf(Props(new ComputeChild(algorithmName, userProperties, username, algId, token.token)))
      val child = Future(c) 
      // store the compute child in the algorithm map
      algorithms += (algId -> child)
      // poll the child for the initial status
      val dest = sender // avoids dynamic scope problems in future
      child.map { child =>
        (child ? AlgorithmStatusRequest(algId)).mapTo[AlgorithmStatus].map { status =>
          status.renderXml
        } pipeTo dest
      }

    case msg @ AlgorithmStatusRequest(algId) =>
      val dest = sender
      algorithms(algId).map { child =>
        (child ? msg).mapTo[AlgorithmStatus].map { status =>
          status.renderXml
        } pipeTo dest
      }

    case msg @ AlgorithmStdoutRequest(algId) => 
      val dest = sender
      algorithms(algId).map { child =>
        (child ? msg).mapTo[AlgorithmResult].map { result =>
          result.renderXml
        } pipeTo dest
      }

    case msg @ AlgorithmStderrRequest(algId) =>
      val dest = sender
      algorithms(algId).map { child =>
        (child ? msg).mapTo[AlgorithmResult].map { result =>
          result.renderXml                        
        } pipeTo dest        
      }

    case msg @ JobDirRequest(algId) =>
      val dest = sender
      algorithms(algId).map { child =>
        (child ? msg).mapTo[AlgorithmResult].map { result =>
          result.renderXml
        } pipeTo dest
      } 

    // THESE CALLS REMOVED UNTIL ALGORITHM RUNS REIMPLEMENTED

/*    case msg @ RunAlgorithm(algName, colName, args) => 

      // node allocation disabled for single machine use

      val algId = newAlgId
      // val nodeAllocator = system.actorFor("user/nodeAllocator")
      val dest = sender
   
      // val child = (nodeAllocator ? ChildRequest(msg, algId, token)).mapTo[ActorRef]

      val c = system.actorOf(Props(new ComputeChild(msg, algId, token)))
      val child = Future(c)
      algorithms += (algId -> child)
 
      child.map { child =>
        (child ? PollAlg(algId)).mapTo[AlgorithmStatus].map { status =>
          status.renderXml
        } pipeTo dest
      }
      
      
  

    case msg @ AlgStdout(algId) =>
       val dest = sender
       algorithms(algId).map { alg =>
        (alg ? msg).mapTo[AlgorithmResult].map { res =>
          <algorithm>
            <id>{algId}</id>
            {res.renderXml}
          </algorithm> 
         } pipeTo dest
       }

    case msg @ AlgStderr(algId) =>
      val dest = sender
      algorithms(algId).map { alg =>
      (alg ? msg).mapTo[AlgorithmResult].map { res =>
        <algorithm>
          <id>{algId}</id>
          {res.renderXml}
        </algorithm> } pipeTo dest
                           }

    case AlgFile(algId, filename) =>
      sender ! <algFile>{filename}</algFile>

    case ListAgentAlgorithms =>
      val dest = sender
      val stati:List[Future[AlgorithmStatus]] = algorithms.toList.map { case (id,aref) =>
        (aref flatMap { a => (a ? PollAlg(id)).mapTo[AlgorithmStatus] } )}

      Future.sequence[AlgorithmStatus,List](stati) map { li =>
        <algorithms>
          {for (s <- li) yield {s.renderXml}}
        </algorithms>
      } pipeTo dest
*/

    case m => println(m)

  }

  // a means to create unique algorithm ids
  def newAlgId: String = {
    UUID.randomUUID.toString
  }

}

