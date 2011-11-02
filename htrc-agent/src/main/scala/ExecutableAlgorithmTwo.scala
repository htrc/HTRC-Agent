
package htrcagent

import java.io.File
import scala.sys.process._
import akka.actor.Actor
import akka.actor.Actor._
import javax.ws.rs.{GET, Path, Produces}
import akka.actor.Actor.registry._
import akka.routing.Routing.Broadcast
import akka.routing.Routing
import akka.routing.CyclicIterator
import java.security.cert.X509Certificate
import java.security.PrivateKey
import scala.collection.mutable.HashMap
import akka.actor.ActorRef
import javax.ws.rs.PathParam
import java.util.UUID
import edu.indiana.d2i.registry._
import java.net.URI
import java.util.Date
//import org.wso2.carbon.registry.ws.client.solrsearchregistration.GetSOLRIndexWSRegistryClient
import org.slf4j.{Logger,LoggerFactory}
import java.util.Properties
import java.io.{File, BufferedReader, InputStreamReader, FileOutputStream}
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import java.io.FileInputStream
import java.io.FileWriter
import java.io.BufferedWriter
import java.util.ArrayList
import org.apache.commons.io.FileUtils
import java.lang.ProcessBuilder
import java.lang.Process
import javax.ws.rs.PUT
import javax.ws.rs.Consumes
import scala.xml.NodeSeq
import javax.xml.bind.annotation.XmlRootElement
import java.io.InputStream
import scala.xml.Node
import javax.xml.bind.JAXBElement
import scala.xml.XML

class ExecutableAlgorithmTwo(
		userID: String,
		algID: String,
		workingDir: String,
		algoName: String,
		collectionName: String,
		userArgs: List[String]
) extends Loggable {

  val ourRegistry = actorFor[RegistryActor].get

  val out = new StringBuilder
  val err = new StringBuilder
  
  val plogger = ProcessLogger(
      (o: String) => out.append(o + "\n"),
      (e: String) => err.append(e + "\n"))
  
  var sysprocess: scala.sys.process.ProcessBuilder = null
      
  def initialize(): Unit = {
  
      val eprMap = new HashMap[String,String]
      eprMap += ("solrEPR" -> (ourRegistry ? SolrURI).as[String].get)
      eprMap += ("cassandraEPR" -> (ourRegistry ? CassandraURI).as[String].get)
      eprMap += ("registryEPR" -> "fakeRegistryEPR")
    
      val volumeIDs:List[String] = (ourRegistry ? GetCollectionVolumeIDs(collectionName)).as[List[String]].get
      val volumesTextFile = (ourRegistry ? WriteVolumesTextFile(volumeIDs, workingDir)).as[String].get
    	     
      val props = AgentUtils.createAlgorithmProperties(eprMap, userArgs=userArgs, volumeIDs,volumesTextFile )
  
      val algoExecutable: String = (ourRegistry ? GetAlgorithmExecutable(algoName,workingDir)).as[String].get
  
      val propFile = new File(workingDir + File.separator + "props.cfg")
      AgentUtils.storePropertiesFileForAlgorithm(props, new BufferedWriter(new FileWriter(propFile)))
      
      sysprocess = scala.sys.process.Process(List("java", "-jar", algoExecutable, "props.cfg"), new File(workingDir))
      
  }
  
  def run(): AlgorithmRunResultStatus = {
    
      val exitCode: Int = sysprocess ! plogger
     
      val fileResults = AgentUtils.findFilesMatchingPattern(pattern="""out-.*txt""", dir=workingDir).map((f) => FileResult(userID, algID, workingDir, f.getName()))
      
      val algoResults = AlgorithmResultSet(userID, algID, (StdoutResult(userID, algID, out.toString) :: StderrResult(userID, algID, err.toString) :: fileResults) :_*)
      
      ourRegistry ! PostResultsToRegistry(userID, algID, algoResults)
         
      if(exitCode == 0) {
          Finished(new Date, algID, workingDir, algoResults)
      } else {
          Crashed(new Date, algID, workingDir, algoResults)
      }
  }
  
}