
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

  // the cleanup function! what a mess!
  def cleanup(): List[AlgorithmResult] = {

    // helper for printing to a file
    def printToFile(f: File)(op: PrintWriter => Unit) {
      val p = new PrintWriter(f)
      try { op(p) } finally { p.close() }
    }

    // mkdir on result storage location
    val storageDir = {
      val rootDir = HtrcProps.resultStoragePath
      val path = rootDir + File.separator + props.username + File.separator + jobId
      (new File(path)).mkdirs()
      path
    }
      
    // since we use this, the web-path
    val urlPath = props.username + "/" + jobId
    // write stdout
    printToFile(new File(storageDir + File.separator + "stdout.txt")) { p =>
      p.println(out.toString)
                                                                 }
      
    // write stderr
    printToFile(new File(storageDir + File.separator + "stderr.txt")) { p =>
      p.println(err.toString)
                                                                 }
    
    val cpProcess = SProcess("cp -r " + resultDir + " " + storageDir + "/" + props.outputDir)
    val exitCode = cpProcess !

    // rm -rf the right thing! this could be EXCITING!
    val rmrf = SProcess("echo deleting: " + workingDir)
    val exitCode2 = rmrf !
    
    val moreResults: List[AlgorithmResult] = (for(r <- props.resultNames) yield {
      DirectoryResult(urlPath+"/"+props.outputDir+"/"+r)
    }).toList

    moreResults ::: List(StdoutResult(urlPath+"/stdout.txt"),
                         StderrResult(urlPath+"/stderr.txt"))
    
  }

  val jobId = props.jobId

  computeParent ! WorkerUpdate(Staging(props))

  // required for actor stuff
  import context._

  // create the algorithm's working directory
  val workingDir = {
    val rootDir = HtrcProps.workingDirRoot
    (new File(rootDir + File.separator + jobId)).mkdirs()
    rootDir + File.separator + jobId
  }

  // create the internal folder to store results
  val resultDir = {
    val path = workingDir+"/"+props.outputDir
    (new File(path)).mkdir()
    path
  }
     
  // create a pair of mutable strings and set them up as the destination for 
  // stdout and stderr
  val out = new StringBuilder
  val err = new StringBuilder

  val plogger = SProcessLogger(
    (o: String) => out.append(o + "\n"),
    (e: String) => err.append(e + "\n"))

  // need to get registry information into the directory
  val registryFinished = registry ? FetchRegistryData(props.registryData, workingDir)
  val registryCollections = registry ? FetchRegistryCollections(props.collections, workingDir, props.username)
  val port = actorFor("akka://htrc/user/portAllocator") ? PortRequest

  // also write props
  props.write(workingDir)

  registryFinished.mapTo[Boolean] map { b =>
    registryCollections.mapTo[Boolean] map { b =>
      port.mapTo[Int] map { p =>
    // ignore the registry's status here for now...

    val sysProcess = SProcess("bash " + props.runScript, new File(workingDir), ("HTRC_MEANDRE_PORT", p.toString))

    computeParent ! WorkerUpdate(Running(props))
    val exitCode: Int = sysProcess ! plogger

    val results = cleanup()

    if(exitCode == 0) {
      computeParent ! WorkerUpdate(Finished(props, results))
      computeParent ! ResultUpdate(results)
    } else {
      computeParent ! WorkerUpdate(Crashed(props, results))
      computeParent ! ResultUpdate(results)
    }
  }}}

}
