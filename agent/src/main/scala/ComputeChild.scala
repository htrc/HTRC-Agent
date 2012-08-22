
package htrcagent

import httpbridge._

import akka.actor.{ Actor, ActorRef, Props }
import akka.actor.Actor._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.{ ask, pipe }
import akka.dispatch.{ Future, Await }
import java.util.Date
import scala.xml._
import scala.collection.mutable.ListBuffer

class ComputeChild(algorithmName: String, userProperties: NodeSeq, username: String, algId: String, token: String) extends Actor {

  import context._

  var status: AlgorithmStatus = Prestart(new Date, algId)
  val results = new ListBuffer[AlgorithmResult]

  val algorithm = Algorithm(algorithmName, userProperties, username, algId, token, self)

  def receive = {
    case AlgorithmStatusRequest(algId) =>
      sender ! status
    case WorkerUpdate(newStatus) =>
      status = newStatus
    case ResultUpdate(result) =>
      results += result
  }

}

