
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

// The place for global state.

import scala.collection.immutable.HashMap
import akka.agent.Agent
import scala.concurrent.stm._
import akka.actor.{ ActorSystem, ActorRef, Props }
import java.util.UUID
import scala.collection.mutable.{ HashMap => MHashMap }
import com.typesafe.config.ConfigFactory
import java.io._

object HtrcSystem {

  // the actor system hosting everything
  val system = ActorSystem("htrc")

  // make the system available implicitly
  implicit val htrcSystem = system

  // System Actors 
  //   * These need to be replaced with proper path-based lookups /
  //     routers / supervised.
  
  val agentBuilder = system.actorOf(Props(new AgentBuilder), name = "agentBuilder")

  val jobCreator = system.actorOf(Props(new JobCreator), name = "jobCreator")

  val localMachineResource = system.actorOf(Props(new LocalMachineResource), 
                                                  name = "localMachineResource")

  val registry = system.actorOf(Props(new Registry), name = "registry")

}

// our global store of what agents exist
// includes operations to atomically add, remove, or lookup agents

object HtrcAgents {

  // make the actor system implicitly available
  import HtrcSystem._

  // the global store of what agents exist
  val agents = Agent(new HashMap[HtrcUser, ActorRef])

  def addAgent(user: HtrcUser, ref: ActorRef) {
    atomic { txn =>
      agents send { agents.get + (user -> ref) }
    }
  }

  def removeAgent(user: HtrcUser) {
    atomic { txn =>
      agents send { agents.get - user }
    }
  }

  def lookupAgent(user: HtrcUser): Option[ActorRef] = {
    agents.get.get(user)
  }

}

// Utility functions that don't have a home.

object HtrcUtils {

  def newJobId: String =
    UUID.randomUUID.toString

  // Supposedly date isn't threadsafe, and I'm not doing anything
  // about that. This is what we call "optimistic concurrency"...

  def date: String = {
    val raw = new java.util.Date
    val df = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    df.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    df.format(raw)
  }

  def writeFile(body: String, name: String, user: HtrcUser, id: JobId): String = {
    // we compute what the appropriate destination is from the user and id
    val root = HtrcConfig.resultDir
    val dest = root + "/" + user + "/" + id
    // check if the folder exists
    (new File(dest)).mkdirs()
    val writer = new PrintWriter(new File(dest+"/"+name))
    writer.write(body)
    writer.close()
      user + "/" + id + "/" + name
  }
  
}

// The global store of configuration information.

object HtrcConfig {

  private val config = ConfigFactory.load("htrc.conf")

  val savedJobLocation = config.getString("htrc.registry.saved_job_location")

  val rootResultUrl = config.getString("htrc.results.url")
  val resultDir = config.getString("htrc.results.location")

  val registryHost = config.getString("htrc.registry.host")
  val registryVersion = config.getString("htrc.registry.version")
  val registryPort = config.getInt("htrc.registry.port")

  val systemVariables = new MHashMap[String,String]
  systemVariables += ("data_api_url" -> config.getString("htrc.data_api.url"))
  systemVariables += ("solr_proxy" -> config.getString("htrc.solr_proxy"))
  systemVariables += ("output_dir" -> config.getString("htrc.output_dir"))

}
