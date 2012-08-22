
// management of the htrc.conf file

package htrcagentclient

import com.typesafe.config.ConfigFactory

object HtrcProps {

  private val config = ConfigFactory.load("htrc.conf")

  val debugUser = config.getString("htrc.debug.user")
  val debugPass = config.getString("htrc.debug.pass")
  val agentRoot = config.getString("htrc.agent.root")

}
