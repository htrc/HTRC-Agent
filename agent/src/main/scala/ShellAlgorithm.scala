
// the simplest type of algorithm

package htrcagent

import httpbridge._

import akka.actor.{ Actor, ActorRef, Props }
import akka.actor.Actor._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.{ ask, pipe }
import java.util.Date
import java.util.UUID
import java.io.File
import java.io.PrintWriter
import scala.sys.process.{ Process => SProcess }
import scala.sys.process.{ ProcessLogger => SProcessLogger }
import scala.sys.process.{ ProcessBuilder => SProcessBuilder }

class ShellAlgorithm(propss: AlgorithmProperties, computeParent: ActorRef) extends Algorithm {

  val props = propss
  val jobId = props.algId

  computeParent ! WorkerUpdate(Initializing(new Date, jobId, props.jobName, props.username, props.algorithmName))

  // required for actor stuff
  import context._

  // create the algorithm's working directory
  val workingDir = {
    val rootDir = "agent/agent_working_directory"
    (new File(rootDir + File.separator + jobId)).mkdir()
    rootDir + File.separator + jobId
  }

  // create the internal folder to store results
  val resultDir = {
    val str = "agent/agent_working_directory/"+jobId+"/"+props.outputDir
    (new File(str)).mkdir()
    str
  }
     
  // create a pair of mutable strings and set them up as the destination for 
  // stdout and stderr
  val out = new StringBuilder
  val err = new StringBuilder

  val plogger = SProcessLogger(
    (o: String) => out.append(o + "\n"),
    (e: String) => err.append(e + "\n"))

  var sysProcess = SProcess("bash " + props.runScript, new File(workingDir))

  // need to get registry information into the directory
  val registryFinished = registry ? FetchRegistryData(props.registryData, workingDir)
  val registryCollections = registry ? FetchRegistryCollections(props.collections, workingDir, props.username)

  // also write props
  props.write(workingDir)

  registryFinished.mapTo[Boolean] map { b =>
    registryCollections.mapTo[Boolean] map { b =>
    // ignore the registry's status here for now...

    computeParent ! WorkerUpdate(Running(new Date, jobId, props.jobName, props.username, props.algorithmName))
    val exitCode: Int = sysProcess ! plogger

    if(exitCode == 0) {
      computeParent ! WorkerUpdate(Finished(new Date, jobId, props.jobName, props.username, props.algorithmName))
      computeParent ! StdoutResult(out.toString)
      computeParent ! StderrResult(err.toString)
      computeParent ! DirResult(resultDir)
    } else {
      computeParent ! WorkerUpdate(Crashed(new Date, jobId, props.jobName, props.username, props.algorithmName))
      computeParent ! StdoutResult(out.toString)
      computeParent ! StderrResult(err.toString)
      computeParent ! DirResult(resultDir)
    }
  }}

}

