
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

// Receives requests to run local machine jobs, creates the
// appropriate actor

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import akka.event.Logging
import scala.concurrent.duration._

class LocalMachineResource extends ComputeResource {
  
  // The behavior of a local machine resource is very simple, an Odin
  // resource will be much more sophisticated.

  // We simply create actors, give them the inputs, and let them run
  // shell scripts. Super simple.

  // due to the magic of generic programming all we define here is the
  // createJob function

  def createJob(user: HtrcUser, inputs: JobInputs, id: JobId): ActorRef = {


    log.debug("LOCAL_MACHINE_RESOURCE_CREATING_JOB\t{}\t{}\tJOB_ID: {}",
             user.name, "ip", id)
    
    // create a child actor of type LocalMachineJob
    val child = context.actorOf(Props(new LocalMachineJob(user, inputs, id)), name = id.id)

    // return it?
    child

  }

}
