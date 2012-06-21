
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

  implicit val timeout:Timeout = Timeout(10 seconds)

  import context._

  restartRegistry

  // for now we hardcode the algorithm and collection list to use

  val rawAlgorithmList = getXmlResource("htrc/agent/algorithm_lists/debug_9")
  val algorithms = ((rawAlgorithmList \ "algorithm") map (a => AlgorithmInfo(a))).toList
  //println(algorithms)
  //println(algorithms map (a => a.name))

  val rawCollectionList = getXmlResource("htrc/agent/collection_lists/debug_1")
  val collections = ((rawCollectionList \ "collection") map (c => CollectionInfo(c))).toList
  //println(collections)

  def receive = {

    case GetAlgorithmInfo(name) =>
      
      sender ! (algorithms find (_.name == name)).get

    case ListAvailibleAlgorithms =>
      sender ! algorithms.map(_.name)

    case ListAvailibleCollections =>
      sender ! collections.map(_.name)

    // this returns the string form of the command to run
    case GetAlgorithmExecutable(algName, workingDir) =>
      val info = (algorithms find (_.name == algName)).get
      val executable = getBinaryResource(info.path)
      binaryToFile(executable, workingDir+"/"+info.executable)
      sender ! true

    case GetAlgorithmData(colName, workingDir) =>
      sender ! true

    case WriteDependencies(alg, workingDir) =>
      val dependencies = (algorithms find (_.name == alg)).get.dependencies
      dependencies map (_ match { case (k,v) => binaryToFile(getBinaryResource(v), workingDir+"/"+k) })
      sender ! true

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

}
