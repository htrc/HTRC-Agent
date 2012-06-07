
package htrcagent

import akka.actor.Actor
import akka.actor.Actor._
import scala.collection.mutable.HashMap

import akka.actor.Props

import akka.dispatch.Future
import akka.dispatch.Future._
import akka.dispatch.Await
import akka.pattern.ask
import akka.util.duration._
import akka.util.Timeout

import org.apache.axis2.context.ConfigurationContext
import org.apache.axis2.context.ConfigurationContextFactory
import org.wso2.carbon.registry.core.{ Registry, Comment, Resource }
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient

import scala.xml._

class RegistryActor extends Actor with Wso2Registry {

  implicit val timeout:Timeout = Timeout(5 seconds)

  import context._

  restartRegistry

  val algs = new AlgorithmList("htrc/agent/algorithm_lists/debug_1")
  val cols = new CollectionList("htrc/agent/collection_lists/debug_1")

  def receive = {

    case ListAvailibleAlgorithms =>
      //restartRegistry
      //testRegTwo
      sender ! algs.names

    case ListAvailibleCollections =>
      sender ! cols.names

    case GetAlgorithmExecutable(algName, workingDir) =>
      // need to do something real
      sender ! "echo 120"

    case GetAlgorithmData(colName, workingDir) =>
      //need to do something real
      sender ! true

  }

  def testRegTwo = {

    val algs = new AlgorithmList("htrc/agent/algorithm_lists/debug_1")
    println(algs.algorithms)

//    val c = registry.get("htrc/agent/collection_lists/testing")
//     val cols = c.getContent
//    println(cols)
    
    val cols = new CollectionList("htrc/agent/collection_lists/debug_1")
    println(cols.collections)

    //putFile("/htrc/agent/algorithms/factorial.sh", "agent_working_directory/factorial.sh")

    

  }

  def testRegistry = {

    restartRegistry
    
    val resource = registry.newResource
    resource.setContent("Hello, htrc")
    registry.put("/testing", resource)
    println("Resource added to /testing!")
    
    val comment = new Comment()
    comment.setText("did I really have to call settext instead of pass an argument?")
    registry.addComment("/testing", comment)
    println("comment added to resource!")

    val got = registry.get("/testing")
    println("The resource is: " + resource.getContent)

  }

}

trait Wso2Registry {

  def initialize: WSRegistryServiceClient = {

    System.setProperty("javax.net.ssl.trustStore", "wso2-config/client-truststore.jks")
    System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon")
    System.setProperty("javax.net.ssl.trustStoreType", "JKS")
    
    val axis2repo = "wso2-config/client"
    val axis2conf = "wso2-config/axis2_client.xml"
    
    val configContext = ConfigurationContextFactory.
    createConfigurationContextFromFileSystem(axis2repo, axis2conf)
    
    val username = "admin"
    val password = "BillionsOfPages11"
    val serverUrl = "https://coffeetree.cs.indiana.edu:9445/services/"

    new WSRegistryServiceClient(serverUrl, username, password, configContext)

  }

  var registry: WSRegistryServiceClient = null

  def restartRegistry = {
    registry = initialize
  }

  def getXmlResource(path: String): NodeSeq = {
    val got = registry.get(path)
    got.getContent
    val is = got.getContentStream
    XML.load(is)
  }

  def getBinaryResource(path: String): Array[Byte] = {
    val got = registry.get(path)
    got.getContent.asInstanceOf[Array[Byte]]
  }

  def binaryToFile(bytes: Array[Byte], writePath: String) {
    val out = new java.io.FileOutputStream(writePath)
    out.write(bytes)
    out.close()
  }

  def makeDir(path: String): String = {
    if (registry.resourceExists(path))
      path
    else {
      val collection = registry.newCollection
      registry.put(path, collection) 
    }
  }

  def putResource(path: String, res: Resource): String = {
    registry.put(path, res)
  }

  def putFile(destPath: String, filePath: String): String = {
    putResource(destPath, BinaryResource(new java.io.FileInputStream(filePath)))
  }

  object BinaryResource {
    def apply(is: java.io.InputStream): Resource = {
      val res = registry.newResource
      res.setContentStream(is)
      res
    }
  }

  object TextResource {
    def apply(str: String): Resource = {
      val res = registry.newResource
      res.setContent(str)
      res
    }
  }

  object XmlResource {
    def apply(xml: Elem): Resource = {
      val res = registry.newResource
      res.setContent(xml.toString)
      res
    }
  }

  // collection list
  class CollectionList(path: String) {
    
    def in = getXmlResource(path)
    def collections: List[RegCol] = 
      ((in \\ "name" map {_.text}) zip (in \\ "path" map {_.text})).toList.map {
        col => RegCol(col._1, col._2)}

    def names: List[String] = 
      collections map {_.name}

  }

  case class RegCol(name: String, path: String)

  // represent an algorithm list xml file stored in registry
  // to start a simple list of algorithms
  class AlgorithmList(path: String) {

    def in = getXmlResource(path)
    def algorithms:List[RegAlg] = 
      ((in \\ "name" map {_.text}) zip (in \\ "path" map {_.text})).toList.map { alg => RegAlg(alg._1, alg._2) }

    def addAlgorithm(name: String, path: String): String = {
      putResource(path, XmlResource( <algorithms>{for(a <- (RegAlg(name, path) :: algorithms)) yield a.toXml}</algorithms>))
    }

    def names: List[String] =
      algorithms map {_.name}

  }

  case class RegAlg(name: String, path: String) {
    def toXml: Elem = <algorithm><name>{name}</name><path>{path}</path></algorithm>
  }

}
