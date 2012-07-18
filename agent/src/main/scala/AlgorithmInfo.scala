
package htrcagent

// an info package describing what an algorithm is
// loaded from registry

import httpbridge._

import scala.collection.mutable.HashMap
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.duration._
import akka.util.Timeout

import scala.xml._

/*
class LoadedAlgorithmInfo(name: String, list: String, registry: ActorRef) {

  // setup the futures
  implicit val timeout = 10 seconds

  // properties hashmap of ... properties?
  // TODO : Replace with the properties class from java
  var properties: Map[String,String] = null

  // a few properties that should have fields for themselves
  var command = "echo NO_COMMAND_ERROR"
  var dependencies: Map[String,String] = null

  // pull information from registry and construct the info packet
  def load: Unit = {

    // get algorithm info xml
    val raw = (registry ? FetchAlgorithmInfo(name, list)).mapTo[AlgorithmInfo]
    val xmlInfo = raw.getOrElse(<error>no algorithm info found</error>)
    
    properties = xmlInfo.properties
    dependencies = xmlInfo.dependencies
    path = xmlInfo.path
    command = xmlInfo.command

  }

}
*/

case class CollectionInfo(raw: scala.xml.Node) {

  val name = (raw \ "name") text
  val path = (raw \ "path") text

}

case class FetchAlgorithmInfo(name: String, list: String)

case class AlgorithmInfo(raw: scala.xml.Node) {

  val name = (raw \ "name") text
  val path = (raw \ "path") text
  val command = (raw \ "command") text
  val executable = (raw \ "executable") text
  val system_properties = makeMap(raw \ "system_properties")
  val algorithm_properties = makeMap(raw \ "algorithm_properties")
  val user_properties = makeMap(raw \ "user_properties")
  val dependencies = makeMap(raw \ "dependencies")
  lazy val prop_filename = (raw \ "properties_name").head.text

  def makeMap(xs: NodeSeq) = ((xs \ "e") map (n => (n.attributes.value.text, n.text))).toMap

  import java.io._

  def writeProperties(workingDir: String) {

    val props = system_properties ++ algorithm_properties ++ user_properties

    if(props.isEmpty == false) {

      printToFile(new File(workingDir+"/"+prop_filename))(p => {
        props.foreach( _ match { case (k,v) => p.println(k + "=" + v) })
      })
    }

  }

  import java.io.File
  import java.io.PrintWriter

  def printToFile(f: File)(op: PrintWriter => Unit) {
    val p = new java.io.PrintWriter(f)
    try { op(p) } finally { p.close() }
  }
}

// some sample algorithm info xml
/*
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
*/
