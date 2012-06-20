
package htrcagent

import akka.actor.{ Actor, ActorRef, Props }
import akka.actor.Actor._
import akka.util.Timeout
import akka.util.duration._
import akka.pattern.{ ask, pipe }
import java.util.Date
import java.util.UUID

import java.io.File
import scala.sys.process._

class ShellAlgorithm(taskk: RunAlgorithm, algIdd: String) extends Algorithm {

  val task: RunAlgorithm = taskk
  val algId: String = algIdd

  import context._

  val algInfo = (registry ? GetAlgorithmInfo(task.algName))

  val workingDir = {
    val rootDir = "agent_working_directory"
    (new File(rootDir + File.separator + algId)).mkdir()
    rootDir + "/" + algId
  }

  val out = new StringBuilder
  val err = new StringBuilder

  val plogger = ProcessLogger(
    (o: String) => out.append(o + "\n"),
    (e: String) => err.append(e + "\n"))

  var sysProcess: ProcessBuilder = null

  val algReady = (registry ? GetAlgorithmExecutable(task.algName, workingDir))
  val dataReady = (registry ? GetAlgorithmData(task.colName, workingDir))

  val f = for {
    a <- algReady.mapTo[Boolean]
    b <- dataReady.mapTo[Boolean]
    info <- algInfo.mapTo[AlgorithmInfo]
  } yield info

  f.mapTo[AlgorithmInfo].map { info =>

    val unformatedCommand = info.command
    val executable = info.executable
    
    val command = unformatedCommand.format(executable)

    parent ! WorkerUpdate(Running(new Date, algId))

    val makeExecutable = scala.sys.process.Process("chmod +x " + executable, new File("agent_working_directory" + File.separator + algId))

    makeExecutable.run

    sysProcess = scala.sys.process.Process(command, new File("agent_working_directory" + File.separator + algId))

    val exitCode: Int = sysProcess ! plogger

    if(exitCode == 0) {
      parent ! WorkerUpdate(Finished(new Date, algId, workingDir))
      parent ! StdoutResult(out.toString)
    } else {
      parent ! WorkerUpdate(Crashed(new Date, algId, workingDir))
      parent ! StderrResult(err.toString)
    }
  }

}

