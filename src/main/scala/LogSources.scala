
package htrc.agent

// To use slf4j logging in akka I need to define "Log Sources" for
// each class that is logged from

// I really don't know if I need this now, or will later. I'll leave
// it here for now.

import akka.event.LogSource
import akka.actor.ActorSystem

object HtrcLogSources {

  implicit val agentServiceLogSource: LogSource[AgentService] = 
    new LogSource[AgentService] {
      def genString(a: AgentService) = "Agent Service"
      //def genString(a: AgentService, s: ActorSystem) = "Agent Service," + s
    }

}
