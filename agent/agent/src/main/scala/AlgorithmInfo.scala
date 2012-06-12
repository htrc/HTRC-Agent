
package htrcagent

// an info package describing what an algorithm is
// loaded from registry

import scala.collection.mutable.HashMap
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.duration._
import akka.util.Timeout

class AlgorithmInfo(name: String, list: String, registry: ActorRef) {

  // setup the futures
  implicit val timeout = 10 seconds

  // properties hashmap of ... properties?
  // TODO : Replace with the properties class from java
  var properties: Map[String,String] = new HashMap[String,String]

  // a few properties that should have fields for themselves
  var command = "echo NO_COMMAND_ERROR"
  var dependencies: Map[String,String] = new HashMap[String,String] // registry paths

  // pull information from registry and construct the info packet
  def load: Unit = {

    // get algorithm info xml
    val raw = (registry ? FetchAlgorithmInfo(name, list)).mapTo[AlgorithmInfoXml]
    val xmlInfo = raw.getOrElse(<error>no algorithm info found</error>)
    
    properties = xmlInfo.properties
    dependencies = xmlInfo.dependencies
    path = xmlInfo.path
    command = xmlInfo.command

  }

}

case class FetchAlgorithmInfo(name: String, list: String)

case class AlgorithmInfoXml(raw: scala.xml.Node) {

  val name = (raw \ "name") text
  val path = (raw \ "path") text
  val command = (raw \ "command") text
  val properties = makeMap(raw \ "properties")
  val dependencies = makeMap(raw \ "dependencies")

  def makeMap(xs: Node) = ((xs \ "e") map (n => (n.attributes.value.text, n.text))).toMap

}

// some sample algorithm info xml

<algorithm>
  <name>factorial</name>
  <path>/htrc/agent/algorithms/factorial.sh</path>
  <command>factorial.sh</command>
  <properties>
    <e key="argle">foo</e>
    <e key="bargle">bar</e>
  </properties>
  <dependencies>
    <e key="cats">/things/good/cats</e>
    <e key="dogs">/things/meh/dogs</e>
  </dependencies>
</algorithm>


