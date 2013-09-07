
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

// A proxy for the WSO2 Registry metadata store. Currently a stub, but
// will eventually (very shortly) use a RESTful API to make these
// requests.

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import java.io.File
import java.io.PrintWriter
import scala.sys.process.{ Process => SProcess }
import scala.sys.process.{ ProcessLogger => SProcessLogger }
import scala.sys.process.{ ProcessBuilder => SProcessBuilder }
import akka.pattern.pipe

class Registry extends Actor {

  // setup

  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  // logic code

  // For registry-free testing use a local dir and cp from it instead

  def cp(source: String, dest: String): Any = {
    val cmd = "cp %s %s".format(source,dest)
    val proc = SProcess(cmd)
    val exitValue = proc.!
    if(exitValue == 0)
      RegistryOk
    else
      RegistryError("could not copy resource: " + source)
  }

  val storage = "registry_dir"
  
  // receive loop

  val behavior: PartialFunction[Any,Unit] = {
    case m: RegistryMessage =>
      m match {
        case WriteFile(path, name, dir, inputs) =>
          log.debug("REGISTRY_WRITE_FILE\tNAME: {}\tTOKEN: {}",
                   name, inputs.token)
          val dest = sender
          RegistryHttpClient.fileDownload(path, inputs, dir+"/"+name) map { b =>
            if (b) RegistryOk else RegistryError(path)
          } pipeTo dest
        case WriteCollection(name, dir, inputs) =>
          log.debug("REGISTRY_WRITE_COLLECTION\tNAME: {}\tTOKEN: {}",
                   name, inputs.token)
          val dest = sender
          RegistryHttpClient.collectionData(name, inputs, dir+"/"+name) map { b =>
            if (b) RegistryOk else RegistryError(name)
          } pipeTo dest
      }
  }

  val unknown: PartialFunction[Any,Unit] = {
    case m =>
      log.error("registry received unknown message")
  }

  def receive = behavior orElse unknown

}
