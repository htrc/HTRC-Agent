
package htrcagent

import httpbridge._

import akka.actor.{ Props, Actor, ActorRef, PoisonPill }
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
  lazy val rawSavedJobs = (registry ? LoadSavedJobs(username)).mapTo[List[NodeSeq]]
  var modifiedSavedJobs: Option[Future[List[NodeSeq]]] = None

  def registry: ActorRef = actorFor("/user/registryActor")

  def receive = {

    case msg @ SaveJob(jobId) =>
      val dest = sender
      if(algorithms.contains(jobId)) {
        algorithms(jobId) map { c =>
          (c ? msg) pipeTo dest
        }
      } else {
        sender ! <error>Job id: {jobId} does not map to an active job</error>
      }

    case DeleteJob(jobId) =>
      // two cases:
      // 1) not yet saved, poison pill child and remove from map
      // 2) saved, ask registry to delete

      if(algorithms.contains(jobId)) {
        algorithms(jobId) map { c =>
          c ! PoisonPill
        }
        algorithms -= jobId
        
        sender ! <success>deleted job: {jobId}</success>

      } else {
        
        modifiedSavedJobs = Some(modifiedSavedJobs.getOrElse(rawSavedJobs) map { js =>
          js.filter { s => (s \ "job_id").text != jobId }
        })

        (registry ? RegistryDeleteJob(username, jobId)) pipeTo sender
      }


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
      val c = context.actorOf(Props(new ComputeChild(algorithmName, userProperties, username, algId, token.token)))
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

    case ActiveJobStatuses =>
      val dest = sender
      val statusFutures = algorithms.map {
        case (k,v) =>
          v.flatMap { child =>
            (child ? AlgorithmStatusRequest(k)).mapTo[AlgorithmStatus].map { s =>
              s.renderXml
                                                                          }
                   }
      }
      val futureList = Future.sequence(statusFutures)
      futureList.map { jobs =>
        <jobs>
        {for(j <- jobs) yield j}
        </jobs>
                    } pipeTo dest

    case SavedJobStatuses =>
      modifiedSavedJobs.getOrElse(rawSavedJobs).map { saved =>
        <jobs>
        {for(s <- saved) yield s}
        </jobs>
                                                   } pipeTo sender

    case AllJobStatusRequest =>
      val dest = sender
      val statusFutures = algorithms.map {
        case (k,v) =>
          v.flatMap { child =>
            (child ? AlgorithmStatusRequest(k)).mapTo[AlgorithmStatus].map { s =>
              s.renderXml
            }
          }
      }

      val futureList = Future.sequence(statusFutures)
      modifiedSavedJobs.getOrElse(rawSavedJobs).map { saved =>
        futureList.map { jobs =>
          <jobs>
          {for(j <- jobs) yield j}
          {for(s <- saved) yield s}
          </jobs>
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

