
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

  computeParent ! WorkerUpdate(Initializing(new Date, algId))

  // required for actor stuff
  import context._

  // fetch a few props for convienence
  val algId = props.algId

  val workingDir = {
    val rootDir = "agent/agent_working_directory"
    (new File(rootDir + File.separator + algId)).mkdir()
    rootDir + File.separator + algId
  }

  val out = new StringBuilder
  val err = new StringBuilder

  val plogger = SProcessLogger(
    (o: String) => out.append(o + "\n"),
    (e: String) => err.append(e + "\n"))

  var sysProcess = SProcess("bash " + props.runScript, new File(workingDir))

  // need to get registry information into the directory
  val registryFinished = registry ? FetchRegistryData(props.registryData, workingDir)

  // also write props
  props.write(workingDir)

  registryFinished.mapTo[Boolean] map { b =>
    // ignore the registry's status here for now...

    val exitCode: Int = sysProcess ! plogger

    if(exitCode == 0) {
      computeParent ! WorkerUpdate(Finished(new Date, algId))
      computeParent ! StdoutResult(out.toString)
    } else {
      computeParent ! WorkerUpdate(Crashed(new Date, algId))
      computeParent ! StderrResult(err.toString)
    }
  }

}

