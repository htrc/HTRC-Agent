
// the wso2 registry trait provides calls to access the registry

package htrcagent

import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer

import java.util.Properties

import org.apache.axis2.context.ConfigurationContext
import org.apache.axis2.context.ConfigurationContextFactory
import org.wso2.carbon.registry.core.{ Registry, Comment, Resource, Collection }
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient

import scala.xml._

import akka.dispatch._
import akka.util.duration._

trait Wso2Registry {

  import HtrcProps.RegistryProps._

  implicit val system = HtrcSystem.system

  def initialize: WSRegistryServiceClient = {

    // todo : load these registry strings from a config file

    System.setProperty("javax.net.ssl.trustStore", "agent/wso2-config/client-truststore.jks")
    System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon")
    System.setProperty("javax.net.ssl.trustStoreType", "JKS")
    
    val axis2repo = "agent/wso2-config/client"
    val axis2conf = "agent/wso2-config/axis2_client.xml"
    
    val configContext = ConfigurationContextFactory.
    createConfigurationContextFromFileSystem(axis2repo, axis2conf)
    
    new WSRegistryServiceClient(uri, username, password, configContext)

  }

  var registry: WSRegistryServiceClient = null

  def restartRegistry = {
    registry = initialize
  }

  def regOp[T](body: => T, attempts: Int = 0): T = {
    if(attempts <= 3) {
      try {
        body
      } catch {
        case e =>
          restartRegistry
          body
      }
    } else {
      body
    }
  }

  def writeData(data: HashMap[String,String], workingDir: String): Boolean = {

    data foreach { case (k,v) =>
      println("registry writing: " + k + " to: " + v)
      val resource = regOp { registry.get(k) }
      val bytes = resource.getContent.asInstanceOf[Array[Byte]]
      val out = new java.io.FileOutputStream(workingDir + "/" + v)
      out.write(bytes)
      out.close()
    }

    true

  }

  def writeCollections(names: List[String], workingDir: String, username: String): Boolean = {
    val paths = getCollectionPaths(username)
    names foreach { case name =>
      val path = paths.find { _.split('/').last == name }
      path match {
        case Some(path) => 
          writeCollection(path, workingDir)
        case _ => println("None in writeCollections: " + path)
      }
    }

    true
  }
                  
  def writeCollection(path: String, workingDir: String) {
    val resource = regOp { registry.get(path+"/data") }
    val bytes = resource.getContent.asInstanceOf[Array[Byte]]
    val out = new java.io.FileOutputStream(workingDir + "/" + path.split('/').last)
    out.write(bytes)
    out.close()
  }

  def exists(path: String): Boolean = {
    regOp { registry.resourceExists(path) }
  }

  def getAlgorithmPaths(user: String): List[String] = {
    // some constants, to pull from htrc.conf later
    val path = algorithmsPath
    // get the collction objects                                       
    val public = regOp { registry.get(path+"/public").asInstanceOf[Collection] }
    val privateExists = 
      if(user == "public") {
        false
      } else {
        regOp { registry.resourceExists(path+"/private/"+user) }
      }
    val nonPublic = 
      if(privateExists)
        Some(regOp { registry.get(path+"/private/"+user).asInstanceOf[Collection] })
      else
        None
    // get the "children" as strings, combine, return
    val publicRes = public.getChildren.toList
    val privateRes = 
      nonPublic match {
        case Some(c) => c.getChildren.toList
        case _ => Nil
      }
    publicRes ::: privateRes
  }

  def getAlgorithmList(username: String): List[String] =
    getAlgorithmPaths(username) map { _.split('/').last }

  def getAlgorithmInfo(username: String, algorithmName: String): NodeSeq = {
    val key = username+"/"+algorithmName
    if(algorithmProps.contains(key)) {
      algorithmProps(key)
    } else {
      val paths = getAlgorithmPaths(username)
      val algPath = paths.find { _.split('/').last == algorithmName }
      if(exists(algPath.get)) {
        val resource = regOp { registry.get(algPath.get) }
        resource.getContent
        val value = XML.load(resource.getContentStream)
        algorithmProps += (key -> value)
        value
      } else {
        <error>missing property file for: {algorithmName}</error>
      }
    }
  }
  
  def getAlgorithmsXml(user: String): NodeSeq = {
    val paths = getAlgorithmPaths(user)
    val owners = paths.map { _.split('/').reverse.drop(1).head }
    val names = paths.map { _.split('/').reverse.head }
    val props = owners.zip(names) map { case (o,n) => getAlgorithmInfo(o, n) }
    <algorithms>{for(p <- props) yield { p \ "info" }}</algorithms>
  }
    
    
  def getAlgorithmsXmlOld(user: String): NodeSeq = {
    val paths = getAlgorithmPaths(user)
    val result = 
      <algorithms>{
        paths map { p =>
          val resource = regOp { registry.get(p) }
          resource.getContent
          XML.load(resource.getContentStream) \ "info"
       }}</algorithms>
    result
  }

  def getAlgorithmInfo(path: String): NodeSeq = {
    if(exists(path)) {
      val resource = regOp { registry.get(path) }
      resource.getContent
      XML.load(resource.getContentStream)
    } else {
      <error>no properties file for: {path.split('/').last}</error>
    }
  }

  // todo : put an algorithm
  // todo : delete a collection
  // todo : delete an algorithm

  def putCollectionFolder(path: String, data_raw: String, metadata: HashMap[String,String]) {
    // if the private directory doesn't exist create it
    val privPath = path.split('/').reverse.drop(1).reverse.mkString("/")
    val exists = regOp { registry.resourceExists(privPath) }
    if(!exists) {
      val nPrivDir = regOp { registry.newCollection }
      regOp { registry.put(privPath, nPrivDir) }
    }
    // create a directory to hold the two files
    val col = regOp { registry.newCollection }
    regOp { registry.put(path, col) }
    // upload the data
    val data = regOp { registry.newResource }
    data.setMediaType("text/plain")
    data.setContent(data_raw)
    regOp { registry.put(path+"/data", data) }
    // convert properties to file
    val props_raw = <collection_properties>{
      metadata.map { case (k,v) => <e key={k}>{v}</e> }
    }</collection_properties>
    // upload the properties
    val props = regOp { registry.newResource }
    props.setMediaType("text/plain")
    props.setContent(props_raw.toString)
    regOp { registry.put(path+"/properties", props) }
  }

  def getCollectionPaths(user: String): List[String] = {
    // some constants, to pull from htrc.conf later
    val path = collectionsPath
    // get the collction objects
    val public = regOp { registry.get(path+"/public").asInstanceOf[Collection] }
    val privateExists = regOp { registry.resourceExists(path+"/private/"+user) }
    val nonPublic =
      if(privateExists)
        Some(regOp { registry.get(path+"/private/"+user).asInstanceOf[Collection] })
      else
        None
    // get the "children" as strings, combine, return          
    val publicRes = public.getChildren.toList
    val privateRes =
      nonPublic match {
        case Some(c) => c.getChildren.toList
        case _ => Nil
      }
    val res = publicRes ::: privateRes
    res
  }

  def getCollectionList(user: String): List[String] = {
    // just grab the last string in each path
    getCollectionPaths(user) map { s => s.split('/').last }
  }

  // we modify this 
  def getCollectionProperties(path: String): Elem = {
    if(exists(path+"/properties")) {
      val resource = regOp { registry.get(path+"/properties") }
      resource.getContent
      XML.load(resource.getContentStream)
    } else {
      <collection_properties><error>invalid or missing properties</error></collection_properties>
    }
  }

  def getCollectionData(path: String): Elem = {
    if(exists(path+"/data")) {
      val resource = regOp { registry.get(path+"/data") }
      val raw = new String(resource.getContent.asInstanceOf[Array[Byte]])
      <ids>{for( id <- raw.split('\n') ) yield <id>{id}</id>}</ids>
    } else {
     <ids><error>invalid collection data file</error></ids>
    }
  }

  def getCollectionsXmlOrig(user: String): NodeSeq = {
    val paths = getCollectionPaths(user)

    val result = 
      <collections>{
        paths map { path =>
          println("fetching collection: " + path)
          if(exists(path+"/properties")) {
            val resource = regOp { registry.get(path+"/properties") }
            resource.getContent
            XML.load(resource.getContentStream)
          } else {
            <error>invalid properties file for collection: {path.split('/').last}</error>
          }
        }
      }</collections>    
    result
  }

  implicit val timeout = 5 seconds

  // this we memoize, as we don't delete collections we are set!
  var collectionProps = new HashMap[String,NodeSeq]

  // might as well do algorithm details too:
  var algorithmProps = new HashMap[String,NodeSeq]

  def resetCache {
    println("Flushing cache...")
    collectionProps = new HashMap[String,NodeSeq]
    getCollectionsXml("drhtrc")
    algorithmProps = new HashMap[String,NodeSeq]
    getAlgorithmsXml("drhtrc")
  }

  def getCollectionInfo(user: String, name: String): NodeSeq = {
    val key = user+"/"+name
    if(collectionProps.contains(key)) {
      collectionProps(key)
    } else {
      if(exists(collectionsPath+"/"+key)) {
        val resource = regOp { registry.get(collectionsPath+"/"+key+"/properties") }
        resource.getContent
        val value = XML.load(resource.getContentStream)
        collectionProps += (key -> value)
        value
      } else {
        <error>invalid properties file for collection: {key}</error>
      }
    }
  }

  def getCollectionsXml(user: String): NodeSeq = {
    val paths = getCollectionPaths(user)
    val owners = paths.map { _.split('/').reverse.drop(1).head }
    val names = paths.map { _.split('/').reverse.head }
    val props = owners.zip(names) map { case (o,n) => getCollectionInfo(o,n) }
    <collections>{for(p <- props) yield p}</collections>
  }

  def getCollectionsXmlAlsoOld(user: String): NodeSeq = {
    val paths = getCollectionPaths(user)

    val listOfFutures = paths.map { p =>
      Future {
        println("getting props for: " + p)
        if(exists(p+"/properties")) {
          val resource = regOp { registry.get(p+"/properties") }
          resource.getContent
          XML.load(resource.getContentStream)
        } else {
          <error>invalid properties file for collection: {p.split('/').last}</error>
        }
      }
    }

    val fcs = Future.sequence(listOfFutures)
    val cs = Await.result(fcs, 5 seconds).asInstanceOf[List[Elem]]
    val result = 
      <collections>{for(c <- cs) yield c}</collections>
    result
  }
  
  // this doesn't work! it just blows up with an internal class cast failure!
  def putCollection(path: String, data: String, metadata: HashMap[String,String]) {
    val resource = registry.newResource
    val props = new Properties
      metadata map { 
        case (k,v) =>
          props.setProperty(k,v)
      }
    resource.setProperties(props)
    resource.setMediaType("text/plain")
    resource.setContent(data)
    registry.put(path, resource)
  }
    
}
