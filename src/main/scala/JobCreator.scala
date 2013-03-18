
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

// Responsible for creating jobs when provided user input

import akka.actor.{ Actor, Props }
import akka.util.Timeout
import akka.event.Logging
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.pipe

class JobCreator extends Actor {

  // context import
  import context._

  // timeout for futures
  implicit val timeout = Timeout(30 seconds)

  // logging
  val log = Logging(context.system, this)

  // resources
  val localMachineResource = HtrcSystem.localMachineResource

  // This actor should inspect the parameters of the incoming job to
  // figure out what resource it should be dispatched to. That
  // resource is then responsible for actually creating the job.

  val behavior: PartialFunction[Any,Unit] = {
    case m: JobCreatorMessage => 
      m match {
        case msg @ CreateJob(user, inputs, id) =>
          log.info("JOB_CREATION\t{}\t{}\tJOB_ID: {}",
                   user.name, user.ip, id)
          localMachineResource forward msg
      }
  }

  val unknown: PartialFunction[Any,Unit] = {
    case m =>
      log.error("job creator received unhandled message")
  }

  def receive = behavior orElse unknown

}
