
package htrc.agent

// An actor that runs a task at the shell and sends status information
// to the supervisor.

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import scala.sys.process.{ Process => SProcess }
import scala.sys.process.{ ProcessLogger => SProcessLogger }
import scala.sys.process.{ ProcessBuilder => SProcessBuilder }
import java.io.File
import java.io.PrintWriter

class ShellTask(user: HtrcUser, inputs: JobInputs, id: JobId) extends Actor {

  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  log.info("shell task for user: " + user + " job: " + id + " launched")

  parent ! StatusUpdate("Initializing")

  // Build output loggers. These should forward to the
  // supervising actor so they can be returned to the user.
  
  val plogger = SProcessLogger(
    (o: String) => parent ! StdoutChunk(o),
    (e: String) => parent ! StderrChunk(e))

  // At this point we might want to ... interact with the inputs. This
  // would be a set of Futures that when all resolved provide what we
  // need to start.

  // For this test we just run the shell command in the input.

  val sysProcess = SProcess("echo 120") 

  parent ! StatusUpdate("Running")

  log.info("about to execute command: " + "echo 120")
  val exitCode = sysProcess ! plogger

  if(exitCode == 0)
    parent ! StatusUpdate("Finished")
  else
    parent ! StatusUpdate("Crashed")

  // This actor doesn't receive yet, so blank receive. It only sends.

  def receive = {
    case m =>
      log.error("shell task")
  }

}
