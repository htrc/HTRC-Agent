
// this is the master trait for what an algorithm is and does

package htrcagent

import akka.actor.{ Actor, ActorRef, Props }
import akka.dispatch.{ ExecutionContext, Future }
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.ask
import java.util.Date
import scala.xml._

trait Algorithm extends Actor {

  import context._

  implicit val timeout = Timeout(3600 seconds)

  // the thing an algorithm has when created
  val props: AlgorithmProperties
  def registry = actorFor("/user/registryActor")

  // algorithms only send messages, they do not receive them
  def receive = {
    case msg => println("an algorithm received a messag: " + msg)
  }

}

object AlgorithmProps {
  
  // the algorithm object will act as a factory
  // the type of algorithm to build will be in the properties file

  def apply(algorithmName: String,
            userProperties: NodeSeq,
            username: String,
            algId: String,
            token: String): Future[AlgorithmProperties] = {


    val system = HtrcSystem.system
    implicit val timeout = Timeout(60000 milliseconds)
    implicit val executor = system.dispatcher
    val registry = system.actorFor("/user/registryActor")
    
    val f = (registry ? RegistryAlgorithmProperties(algorithmName, username))
    
    f.mapTo[NodeSeq].map { xml =>
      val props = AlgorithmProperties(userProperties, 
                                      xml, 
                                      algorithmName, 
                                      algId,
                                      username,
                                      token)
      props
    }

  }

}
