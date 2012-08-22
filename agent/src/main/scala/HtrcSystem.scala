
// put the actor system in a global object

package htrcagent

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object HtrcSystem {

  val system = ActorSystem("htrc", ConfigFactory.load.getConfig("htrcsingle"))

}
