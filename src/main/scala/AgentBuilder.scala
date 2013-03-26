
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

// Problem: A user with a fresh session may make many queries before
// their agent "exists". Blocking on agent creation in Spray layer
// could have performance impact, nonblocking creation creates a race
// condition.

// Solution: An actor responsible for constructing agent actors that
// do not already exist. If the spray layer fails to find an agent,
// forward message to this actor. This actor builds the agent if
// necessary, otherwise it just forwards the message to an existing
// agent.

import akka.actor.{ Props, Actor }
import akka.event.Logging

class AgentBuilder extends Actor {

  // intialize logger
  val log = Logging(context.system, this)

  // The system needs to know what agents exist, so store a hashmap of
  // them in an actual akka agent. The HtrcAgents objects has this store.
  val agents = HtrcAgents

  def receive = {

    case BuildAgent(user, message) =>
      val agent = agents.lookupAgent(user)
      if(agent == None) {
        // build the agent and forward message to new agent
        val ref = HtrcSystem.system.actorOf(Props(new HtrcAgent(user)), name = user.name)
        agents.addAgent(user, ref)
        ref.forward(message)
      } else {
        // agent already exists, so forward to it
        agent.get.forward(message)
      }
  
      log.debug("\tBUILD_AGENT\t{}\t{}", user.name, "fake")

  }

}
