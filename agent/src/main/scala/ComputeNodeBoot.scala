
package htrcagent

import akka.kernel.Bootable
import akka.actor.{ Props, Actor, ActorSystem }
import com.typesafe.config.ConfigFactory

class ComputeNode extends Bootable {

  val system = ActorSystem("htrcworker", ConfigFactory.load.getConfig("htrcworker"))
  val registry = system.actorOf(Props[RegistryActor], name = "registryActor")
  val solr = system.actorOf(Props[SolrActor], name = "solrActor")
  
  def startup() {

  }

  def shutdown() {
    system.shutdown()
  }

}

object ComputeNodeApp extends Application {
  new ComputeNode
  println("started a compute node")
}
