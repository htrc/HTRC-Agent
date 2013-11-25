
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
import com.typesafe.config.{Config, ConfigFactory, ConfigList}
import java.io._
import scala.concurrent.stm._

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

  val localResourceType = config.getString("htrc.compute.local_resource_type")
  val jobScheduler = config.getString("htrc.compute.job-scheduler")
  val jobThrottling = config.getBoolean("htrc.compute.job_throttling")
  val maxJobs = config.getInt("htrc.compute.max_jobs")

  val savedJobLocation = config.getString("htrc.registry.saved_job_location")

  val rootResultUrl = config.getString("htrc.results.url")
  val resultDir = config.getString("htrc.results.location")

  val registryHost = config.getString("htrc.registry.host")
  val registryVersion = config.getString("htrc.registry.version")
  val registryPort = config.getInt("htrc.registry.port")

  // information regarding compute resource (on which jobs are run)
  val computeResource = "htrc." + localResourceType + "."
  val computeResourceUser = config.getString(computeResource + "user")
  val computeResourceWorkingDir = 
    config.getString(computeResource + "working-dir")
  val dependencyDir = config.getString(computeResource + "dependency-dir")
  val javaCmd = config.getString(computeResource + "java-command")
  val javaMaxHeapSize = config.getString(computeResource + "java-max-heap-size")

  // algWallTimes, maxWalltime are used only in PBSTask
  val walltimesPath = computeResource + "PBS-walltimes"
  val algWalltimes = 
    if (config.hasPath(walltimesPath))
    walltimesConfigToHashMap(config.getConfig(walltimesPath + 
                                              ".algorithm-walltimes"))
    else null
  val maxWalltime = 
    if (config.hasPath(walltimesPath)) 
    config.getString(walltimesPath + ".max-walltime")
    else "10:00:00"

  val numVolsParamName = 
    config.getString("htrc.job-parameters.num-volumes-param-name")
  val algWithNoHeaderInWorksets = 
    config.getStringList("htrc.alg-with-no-header-in-worksets")

  // homeDir is used only in ShellTask
  val homeDirPath = computeResource + "home-dir"
  val homeDir = if (config.hasPath(homeDirPath)) 
                config.getString(homeDirPath)
                else ""

  val systemVariables = new MHashMap[String,String]
  systemVariables += ("data_api_url" -> config.getString("htrc.data_api.url"))
  systemVariables += ("solr_proxy" -> config.getString("htrc.solr_proxy"))
  systemVariables += ("output_dir" -> config.getString("htrc.output_dir"))

  // checks if the given job requires the job workset to contain a header
  // row, such as "volume_id, class, ..."
  def requiresWorksetWithHeader(inputs: JobInputs): Boolean = {
    ! algWithNoHeaderInWorksets.contains(inputs.algorithm)
  }

  // the following method is valid only for PBSTask jobs running on compute
  // resources that require a poll script to check job status, e.g., quarry,
  // bigred2
  def getJobStatusPollScript: String = {
    config.getString(computeResource + "job-status-poll-script")
  }

  def getQsubOptions: String = {
    config.getString(computeResource + "qsub-options")
  }

  def getNumVols(numVolsParamName: String, properties: MHashMap[String, String]): Int = {
    properties.find({case(k, v) => k contains numVolsParamName}) match {
      case Some((key, value)) => value.toInt
      case None => -1
    }
  }

  def getPBSWalltime(inputs: JobInputs): String = {
    val numVols = getNumVols(numVolsParamName, inputs.properties)
    if (numVols != -1) {
      getPBSWalltime(inputs.algorithm, numVols)
    }
    else {
      val algWalltimePath = walltimesPath + ".no-volume-count-walltimes." + 
                            inputs.algorithm
      if (config.hasPath(algWalltimePath)) 
      config.getString(algWalltimePath)
      else maxWalltime
    }
  }

  def getPBSWalltime(algorithm: String, numVols: Int): String = {
    def intInRange(t: (Int, Int, String)): Boolean = {
      t match {
        case (lower, upper, walltime) => 
          if ((lower <= numVols) && (numVols <= upper)) true else false
      }
    }
    if (algWalltimes.isDefinedAt(algorithm)) {
      algWalltimes(algorithm).find(intInRange) match {
        case Some((lower, upper, walltime)) => walltime
        case None => maxWalltime
      }
    }
    else maxWalltime
  }

  def walltimesConfigToHashMap(walltimesConfig: Config): MHashMap[String, List[(Int, Int, String)]] = {
    val result = new MHashMap[String, List[(Int, Int, String)]]

    val walltimes = walltimesConfig.entrySet.iterator
    while (walltimes.hasNext) {
      val elem = walltimes.next
      val algorithm = elem.getKey.asInstanceOf[String]
      val ls = elem.getValue.asInstanceOf[ConfigList]
      var lsTuples: List[(Int, Int, String)] = Nil
      // ls = [[1, 20, "00:30:00"], ...], list of 3-element lists
      val lsIterator = ls.iterator
      while (lsIterator.hasNext) {
        val tuple = lsIterator.next.asInstanceOf[ConfigList]
        val lower = tuple.get(0).unwrapped.asInstanceOf[Int]
        val upper = tuple.get(1).unwrapped.asInstanceOf[Int]
        val walltime = tuple.get(2).unwrapped.asInstanceOf[String]
        lsTuples = (lower, upper, walltime) :: lsTuples
      }
      result += (algorithm -> lsTuples.reverse)
    }
    result
  }
}

object JobThrottler {

  val count: Ref[Int] = Ref(0)

  def addJob() =
    atomic { implicit t =>
      count.transform { _ + 1 }
          }
  
  def removeJob() =
    atomic { implicit t =>
      count.transform { _ - 1 }
          }
  
  def getJobCount =
    atomic { implicit t =>
      count.get
          }
  
  
  def jobsOk: Boolean = {
    if (getJobCount <= HtrcConfig.maxJobs) {
      true
    } else {
      false
    }
  }
  
}

object MeandrePortAllocator {

  val count: Ref[Int] = Ref(30000)
  def get: Int = {
    atomic { implicit t =>
      count.transform { c =>
        if(c > 65000)
          30000
        else
          c + 1
      }
      count.get
    }
  }

}
