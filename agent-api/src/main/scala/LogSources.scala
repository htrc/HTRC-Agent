
/*
#
# Copyright 2013 The Trustees of Indiana University
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
*/


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
