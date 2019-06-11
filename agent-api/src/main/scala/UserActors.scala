
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

import akka.actor.{ Props, Actor, ActorRef }
import akka.event.Logging
import scala.collection.mutable.HashMap

// class that maps users to the corresponding HtrcAgent actors
class UserActors extends Actor {

  // intialize logger
  val log = Logging(context.system, this)

  val userHtrcAgents = new HashMap[HtrcUser, ActorRef]

  def receive = {
    case GetUserActor(user, message) =>
      // log.debug("GetUserActor(" + user + "), userHtrcAgents = " +
      //   userHtrcAgents.mkString("[", ", ", "]"))
      val userHtrcAgent =
        userHtrcAgents.getOrElseUpdate(user, createUserHtrcAgent(user, message))
      // log.debug("After getOrElseUpdate(" + user + "), userHtrcAgents = " +
      //    userHtrcAgents.mkString("[", ", ", "]"))

      userHtrcAgent.forward(message)
  }

  def createUserHtrcAgent(user: HtrcUser, message: AgentMessage): ActorRef = {
    log.info("Building HtrcAgent to handle message "+message+" from user "+user);
    val ref = HtrcSystem.system.actorOf(Props(new HtrcAgent(user)),
      name = user.name)
    ref
  }  

}
