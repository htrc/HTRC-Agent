
package htrcagent

import akka.actor.{ Actor, ActorRef, Props }
import akka.actor.Actor._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.{ ask, pipe }
import java.util.Date
import java.util.UUID

import java.io.File
import scala.sys.process._

trait Algorithm extends Actor {

  val task: RunAlgorithm

  import context._
  implicit val timeout = Timeout(10 seconds)

  def registry = actorFor("/user/registryActor")
  val algId: String

  def receive = {
    case m => println("compute worker received message")
  }

}

  
  
