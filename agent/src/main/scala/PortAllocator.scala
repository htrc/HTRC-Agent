
package htrcagent

import akka.actor._

// this object just allocates ports to algorithm runs,
// primarily just to make Meandre happy

class PortAllocator extends Actor {

  import context._

  var current = 30000

  def receive = {
    case PortRequest =>
      if(current >= 62000) {
        current = 30000
      }
      current += 1
      sender ! current
  }

}
      

