
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

// Type aliases and containers for the various types of data passed
// around the system.

import scala.concurrent.Future
import akka.actor.{ ActorRef }
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import akka.util.Timeout
import scala.concurrent.duration._
import scala.xml._

// Jobs currently being managed by an actor are actually futures
// containing the monitoring actor's address. These are somewhat
// irritating to deal with, so a wrapper class.
case class HtrcJob(ref: Future[ActorRef]) {

  implicit val timeout = Timeout(30 seconds)

  def dispatch(msg: Any): Future[Any] = {
    ref flatMap { child =>
      child ? msg
    }
  }

}

case class AlgorithmMetadata(raw: NodeSeq)

// job ids are currently represented as strings
case class JobId(id: String) {
  override def toString: String = id
}

// users are currently represented by strings, "value class" allows
// creating a wrapper with no runtime overhead
case class HtrcUser(name: String) {
  override def toString = name
}



