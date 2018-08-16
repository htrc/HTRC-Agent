
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

  // object to create job client script files
  val jobClientScriptCreator = 
     new JobClientScriptCreator(HtrcConfig.skelJobClientScript) 

  // actor to manage job result cache
  val cacheController = system.actorOf(Props(new CacheController), 
                                       name = "cacheController")
}

// our global store of what agents exist
// includes operations to atomically add, remove, or lookup agents

object HtrcAgents {

  // make the actor system implicitly available
  import HtrcSystem._
  import system.dispatcher

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

  def writeFile(body: String, name: String, user: String, id: JobId): String = {
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

  def writeFile(body: String, name: String, user: HtrcUser, id: JobId): String =
    writeFile(body, name, user.name, id)

  // appends to a given file (full path has to be specified), even if the
  // file does not exist; however, intermediate dirs in the specified path
  // must exist
  def appendToFile(content: String, file: String) = {
    val writer = new FileWriter(file, true) 
    writer.write(content)
    writer.close()
  }

  def fileExists(file: String): Boolean = 
    (new File(file)).exists

  // append to possibly non-empty stdout.txt, stderr.txt files; arg "file" is
  // expected to be either "stdout.txt" or "stderr.txt"
  def appendToStdOutErrFile(content: String, fullFilePath: String) = {
    if (content.length > 0) {
      val divider = "\n\n" + "=" * 81 + "\n"

      println("fileExists(" + fullFilePath + ") = " + fileExists(fullFilePath))

      if (fileExists(fullFilePath))
        appendToFile(divider + content, fullFilePath)
      else appendToFile(content, fullFilePath)
    }
  }
}

// The global store of configuration information.

object HtrcConfig {

  val defaultConfFilePath = "/etc/htrc/agent/config/htrc.conf"
  val confFilePathVar = "HTRC_AGENT_CONF_FILE"

  // an external path to the configuration file is used only when the agent is
  // run in dummy mode, i.e., in the development environment
  val confFilePath =
    sys.env.getOrElse(confFilePathVar, defaultConfFilePath)

  private val config =
    if (HtrcUtils.fileExists(confFilePath)) {
      ConfigFactory.parseFile(new File(confFilePath))
    } else {
      ConfigFactory.load("htrc.conf")
    }

  val localResourceType = config.getString("htrc.compute.local_resource_type")
  val jobScheduler = config.getString("htrc.compute.job-scheduler")
  val jobThrottling = config.getBoolean("htrc.compute.job_throttling")
  val maxJobs = config.getInt("htrc.compute.max_jobs")

  val savedJobLocation = config.getString("htrc.registry.saved_job_location")

  val rootResultUrl = config.getString("htrc.results.url")
  val resultDir = config.getString("htrc.results.location")

  val localAgentWorkingDir = config.getString("htrc.local_agent_working_dir")

  val skelJobClientScript = config.getString("htrc.skel_job_client_script")
  val jobClientScript = config.getString("htrc.job_client_script")

  val keystoreForOutgoingReqs = config.getString("htrc.keystore_for_outgoing_requests")
  val keystorePasswd = config.getString("htrc.keystore_passwd") 

  val agentEndpoint = config.getString("htrc.agent_endpoint");

  // val registryHost = config.getString("htrc.registry.host")
  // val registryVersion = config.getString("htrc.registry.version")
  // val registryPort = config.getInt("htrc.registry.port")
  val registryUrl = config.getString("htrc.registry.url")

  // val idServerHttpProtocol = config.getString("htrc.id_server.http_protocol")
  // val idServerHost = config.getString("htrc.id_server.host")
  // val idServerPort = config.getInt("htrc.id_server.port")
  // val idServerTokenEndPoint = config.getString("htrc.id_server.token_endpoint")
  // val idServerTokenUrl = idServerHttpProtocol + "://" + idServerHost + ":" + 
  //                        idServerPort + idServerTokenEndPoint 
  val idServerTokenUrl = config.getString("htrc.id_server.token_url")

  val clientIdFormFieldName = 
    config.getString("htrc.id_server.client_id_form_field_name")
  val clientSecretFormFieldName = 
    config.getString("htrc.id_server.client_secret_form_field_name")
  val grantTypeFormFieldName = 
    config.getString("htrc.id_server.grant_type_form_field_name")
  val clientCredentialsGrantType = 
    config.getString("htrc.id_server.client_credentials_grant_type")
  val accessTokenFieldName = 
    config.getString("htrc.id_server.access_token_field_name")

  // id, secret of the OAuth client used by the agent to interact with the
  // identity server
  val agentClientId = config.getString("htrc.id_server.agent_client_id")
  val agentClientSecret = config.getString("htrc.id_server.agent_client_secret")

  // configuration params for the job result cache
  val useCache = config.getBoolean("htrc.job_result_cache.use_cache")
    // default value for the "usecache" query param of "/algorithm/run"
  val cacheJobs = config.getBoolean("htrc.job_result_cache.cache_jobs")
    // whether jobs should be added to the cache after successful execution
  val cacheFilePath = config.getString("htrc.job_result_cache.cache_file_path")
  val cacheSize = config.getInt("htrc.job_result_cache.cache_size")
  val cleanUpCachedJobsOnStartup = config.getBoolean("htrc.job_result_cache.cleanup_cached_jobs_on_startup")
  val cachedJobsDir = config.getString("htrc.job_result_cache.cached_jobs_dir")
  val cacheWriteInterval = cacheWriteIntervalInSec(config.getString("htrc.job_result_cache.cache_write_interval"))
  val cacheJobsOnPrivWksets = config.getBoolean("htrc.job_result_cache.cache_jobs_on_priv_wksets")
     // whether cache read/write should be performed for job submissions for
     // whom at least one input workset is private

  // information regarding compute resource (on which jobs are run)
  val computeResource = "htrc." + localResourceType + "."
  val computeResourceUser = config.getString(computeResource + "user")
  val computeResourceWorkingDir = 
    config.getString(computeResource + "working-dir")
  val dependencyDir = config.getString(computeResource + "dependency-dir")
  val javaCmd = config.getString(computeResource + "java-command")
  val javaMaxHeapSize = config.getString(computeResource + "java-max-heap-size")

  // algWallTimes, maxWalltime are used in PBSTask and SLURMTask
  val walltimesPath = computeResource + "walltimes"
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
  systemVariables += ("data_api_host" -> config.getString("htrc.data_api.host"))
  systemVariables += ("data_api_port" -> config.getString("htrc.data_api.port"))
  systemVariables += ("data_api_epr" -> config.getString("htrc.data_api.epr"))
  systemVariables += ("solr_proxy" -> config.getString("htrc.solr_proxy"))
  systemVariables += ("output_dir" -> config.getString("htrc.output_dir"))

  // checks if the given job requires the job workset to contain a header
  // row, such as "volume_id, class, ..."
  def requiresWorksetWithHeader(inputs: JobInputs): Boolean = {
    ! algWithNoHeaderInWorksets.contains(inputs.algorithm)
  }

  def getQsubOptions: String = {
    config.getString(computeResource + "qsub-options")
  }

  def getQsubProcReqStr: String = {
    config.getString(computeResource + "qsub-proc-req-str")
  }

  def getDefaultNumNodes: Int = {
    config.getInt(computeResource + "default-num-nodes")
  }

  def getDefaultNumProcessorsPerNode: Int = {
    config.getInt(computeResource + "default-num-processors-per-node")
  }

  def getDefaultWalltime: String = {
    config.getString(computeResource + "default-walltime")
  }

  def getDefaultJavaMaxHeapSize: String = {
    config.getString(computeResource + "default-java-max-heap-size")
  }

  def getSbatchOptions: String = {
    config.getString(computeResource + "sbatch-options")
  }

  def getNumVols(numVolsParamName: String, properties: MHashMap[String, String]): Int = {
    val res = properties.find({case(k, v) => k contains numVolsParamName})
    (res map {case(key, value) => value.toInt}) getOrElse -1
    // properties.find({case(k, v) => k contains numVolsParamName}) match {
    //   case Some((key, value)) => value.toInt
    //   case None => -1
    // }
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
    val result = 
      if (algWalltimes.isDefinedAt(algorithm)) {
        val res = algWalltimes(algorithm).find(intInRange)
        (res map {case(l, u, walltime) => walltime}) getOrElse maxWalltime
        // algWalltimes(algorithm).find(intInRange) match {
        //   case Some((lower, upper, walltime)) => walltime
        //   case None => maxWalltime
      }
      else maxWalltime
    // println("getPBSWalltime(" + algorithm + ", " + numVols + ") = " + result)
    result
  }

  // return "user@domain" used to access specified computeResource
  def targetUser(computeResource: String) =
    config.getString("htrc." + computeResource + ".user")

  // return the path of the working directory for jobs on the specified
  // computeResource
  def targetWorkingDir(computeResource: String) =
    config.getString("htrc." + computeResource + ".working-dir")

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

  // convert a string of the form "hh:mm:ss" into seconds; if the given
  // string does not match the required pattern, then a default value of 1
  // hour = 3600 seconds is returned
  def cacheWriteIntervalInSec(str: String) = {
    val hhmmss = """^(?:(?:(\d\d):)?(\d\d):)?(\d\d)$""".r
    val defaultResult = 1*60*60 // 1 hour
    str match {
      case hhmmss(h, m, s) => 
	val res = ((h.toInt)*60*60 + (m.toInt)*60 + s.toInt)
        if (res == 0) defaultResult else res
      case _ => defaultResult
    }
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
