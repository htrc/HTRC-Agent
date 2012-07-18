
package htrcagent

// this actor is a routed "load balancer" that creates compute children
// on a set of nodes using some algorithm. The initial implementation
// will be round robin.

import httpbridge._

import akka.actor.{ Props, Actor }

class NodeAllocator extends Actor {

  import context._

  def receive = {
    case ChildRequest(alg, algId, token) => 
      println(self + " got a child request")
      sender ! actorOf(Props(new ComputeChild(alg, algId, token)), algId)
    case msg: String =>
      println(self + " got: " + msg)
      sender ! "receiver: " + self
  }

}

case class ChildRequest(alg: RunAlgorithm, algId: String, token: Oauth2Token)
