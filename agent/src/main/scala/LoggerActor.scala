
package htrcagent

// since we don't have real logging this actor receives
// messages and prints them

import akka.actor._
import java.io.File
import java.io.PrintWriter

class LoggerActor extends Actor {

  def receive = {

    case SysLog(str) => 
      log("syslog", str)
    case JobLog(id, str) =>
      log("job_log/"+id, str)

  }

  def log(file: String, msg: String) {
    val p = new PrintWriter(file)
    try { p.println(msg) } 
    finally { p.close }
  }

}

case class SysLog(str: String)
case class JobLog(id: String, str: String)

