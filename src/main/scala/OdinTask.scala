
package htrc.agent

// An actor that runs a task at the shell and sends status information
// to the supervisor. In this case the shell task is actually to do a
// remote job submission on Odin.

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import scala.sys.process.{ Process => SProcess }
import scala.sys.process.{ ProcessLogger => SProcessLogger }
import scala.sys.process.{ ProcessBuilder => SProcessBuilder }
import java.io.File
import java.io.PrintWriter
import akka.pattern.ask
import scala.concurrent._
import scala.collection.mutable.HashMap

class OdinTask(user: HtrcUser, inputs: JobInputs, id: JobId) extends Actor {

  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  val registry = HtrcSystem.registry

  log.info("shell task for user: " + user + " job: " + id + " launched")

  parent ! StatusUpdate(InternalStaging)

  // Build output loggers. These should forward to the
  // supervising actor so they can be returned to the user.
  
  val plogger = SProcessLogger(
    (o: String) => parent ! StdoutChunk(o),
    (e: String) => parent ! StderrChunk(e))

  // At this point we might want to ... interact with the inputs. This
  // would be a set of Futures that when all resolved provide what we
  // need to start.

  // We should have all of the information on what inputs we need at
  // this point. First we fetch them, then write them to a working
  // directory.

  // Before we can write to a working directory, we must create one.
  val workingDir = {
    val root = "agent_working_directories"
    (new File(root + File.separator + id)).mkdirs()
    root + File.separator + id
  }

  // Also create the result directory.
  val resultDir = {
    val path = workingDir + File.separator + "job_results"
    (new File(path)).mkdir()
    path
  }

  // Write the properties.
  writeProperties(inputs.properties, inputs.propertiesFileName, workingDir)

  
  // To fetch information from the registry we send it a message with
  // both the registry path and the output path. Instead of responding
  // with the information it writes it out to disk for us. The
  // response we get back is an indication that is has finished.

  // The outputs we need to write are the collections, and the
  // dependencies stored in the registry. Dependencies currently
  // include files.

  val dependenciesReady = inputs.dependencies map { case (path,name) =>
    (registry ? WriteFile(path, name, workingDir)).mapTo[WriteStatus]
  } toList

  // We do the same thing with collections, but our command is
  // different.
  
  val collectionsReady = inputs.collections map { c =>
    (registry ? WriteCollection(c, workingDir)).mapTo[WriteStatus]
  } toList

  // Check if these things are all finished, once they are, continue.

  val supe = parent

  Future.sequence(dependenciesReady ++ collectionsReady) map { statuses =>
    val errors = statuses.collect( _ match { case v @ RegistryError(e) => v })
    if(errors.length != 0) {
      errors.foreach { e =>
        log.error("Registry failed to write resource: " + e)
        supe ! StatusUpdate(InternalCrashed)
      }
    } else {
      // to be here we must have not had errors, so do the work

      // config info, to move out to a file later
      val odin = "hathitrust@odin"

      log.info("workingDir: " + workingDir)

      // Now we need to copy the directory to odin
      val scpCmd = "scp -r %s %s:~/agent_working_directories/"
      val scpRes = SProcess(scpCmd.format(workingDir, odin)) !

      // Our "system" parameters can be set as environment variables
      val env = "HTRC_WORKING_DIR=agent_working_directories/%s".format(id)
      val cmdF = "ssh %s %s srun -N1 bash ~/agent_working_directories/%s/%s"
      val cmd = cmdF.format(odin, env, id, inputs.runScript)
      
      val sysProcess = SProcess(cmd, new File(workingDir))
      log.info("about to execute command: " + cmd)
      
      supe ! StatusUpdate(InternalRunning)
      // Recall from above, this plogger forwards stdin and stdout to
      // the parent
      val exitCode = sysProcess ! plogger

      if(exitCode == 0) {
        // todo : send result info to parent
        supe ! StatusUpdate(InternalFinished)        
      } else {
        supe ! StatusUpdate(InternalCrashed)
      }
    }
  }

  // Helper to write properties file to disk

  def writeProperties(properties: HashMap[String,String],
                      name: String,
                      workingDir: String) {

    def printToFile(f: File)(op: PrintWriter => Unit) {
      val p = new PrintWriter(f)
      try { op(p) } finally { p.close() }
    }

    if(properties.isEmpty == false) {
      printToFile(new File(workingDir + File.separator + name))(p => {
        properties foreach { case (k,v) =>
          if(v != "HTRC_DEFAULT") p.println(k + " = " + v) }
      })
    }
  }

  // This actor doesn't actually receive, be sad if it does.

  def receive = {
    case m =>
      log.error("shell task")
  }

}
