
package htrcagent

// a log method to import

object HtrcUtils {

  val system = HtrcSystem.system

  def sysLog(msg: String) = 
    system.actorFor("/user/logger") ! SysLog(msg)

  def jobLog(id: String, msg: String) =
    system.actorFor("/user/logger") ! JobLog(id, msg)

}
