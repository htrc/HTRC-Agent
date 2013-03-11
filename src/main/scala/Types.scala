
package htrc.agent

// Type aliases and containers for the various types of data passed
// around the system.

import scala.concurrent.Future
import akka.actor.{ ActorRef }
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import akka.util.Timeout
import scala.concurrent.duration._
import scala.xml._

// Jobs currently being managed by an actor are actually futures
// containing the monitoring actor's address. These are somewhat
// irritating to deal with, so a wrapper class.
case class HtrcJob(ref: Future[ActorRef]) {

  implicit val timeout = Timeout(30 seconds)

  def dispatch(msg: Any): Future[Any] = {
    ref flatMap { child =>
      child ? msg
    }
  }

}

case class AlgorithmMetadata(raw: NodeSeq)

// this will actually turn into a real class, but I don't know what
// the representation will be so this is a placeholder
class SavedHtrcJob(val rep: String) extends AnyVal

// job ids are currently represented as strings
case class JobId(id: String) {
  override def toString: String = id
}

// users are currently represented by strings, "value class" allows
// creating a wrapper with no runtime overhead
case class HtrcUser(name: String, ip: String) {
  override def toString = name
}



