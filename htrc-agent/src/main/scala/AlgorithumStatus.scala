

package htrcagent

import scala.io.Source
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


abstract class AlgorithmRunStatus(time: Date, algID: String) {
  def toXML: scala.xml.Elem
}


case class Prestart(time: Date, algID: String) extends AlgorithmRunStatus(time, algID) {
  
  def toXML: scala.xml.Elem = {
    <algorithmRun>
	  <id>{algID}</id>
	  <status>Prestart</status>
	  <lastStatusChange>{time}</lastStatusChange>
	</algorithmRun>
  }
  
}

case class Initializing(time: Date, algID: String) extends AlgorithmRunStatus(time, algID) {
  
  def toXML: scala.xml.Elem = {
    <algorithmRun>
	  <id>{algID}</id>
	  <status>Initializing</status>
	  <lastStatusChange>{time}</lastStatusChange>
	</algorithmRun>
  }
  
}

case class Running(time: Date, algID: String, workingDir: String) extends AlgorithmRunStatus(time, algID) {
  
  def toXML: scala.xml.Elem = {
    <algorithmRun>
	  <id>{algID}</id>
	  <status>Running</status>
	  <lastStatusChange>{time}</lastStatusChange>
	  <workingDir>{workingDir}</workingDir>
	</algorithmRun>
  }
  
}



abstract class AlgorithmRunResultStatus(time: Date, algID: String, workingDir: String, algorithmResults: AlgorithmResultSet) extends AlgorithmRunStatus(time, algID)

case class Finished(time: Date, algID: String, workingDir: String, algorithmResults:AlgorithmResultSet) extends AlgorithmRunResultStatus(time, algID, workingDir, algorithmResults) {
  
  def toXML: scala.xml.Elem = {
    <algorithmRun>
	  <id>{algID}</id>
	  <status>Finished</status>
	  <lastStatusChange>{time}</lastStatusChange>
	  <workingDir>{workingDir}</workingDir>
	  {algorithmResults.toXML}
	</algorithmRun>
  }
  
}

case class Crashed(time: Date, algID: String, workingDir: String, algorithmResults:AlgorithmResultSet) extends AlgorithmRunResultStatus(time, algID, workingDir, algorithmResults) {
  
  def toXML: scala.xml.Elem = {
    <algorithmRun>
	  <id>{algID}</id>
	  <status>Crashed</status>
	  <lastStatusChange>{time}</lastStatusChange>
	  <workingDir>{workingDir}</workingDir>
	  {algorithmResults.toXML}
	</algorithmRun>
  }
  
}



//case class UnableToFindAlgorithm(algoName: String,time: Date) extends AlgorithmRunStatus()


abstract class AlgorithmResult(agentID: String, algID: String) {
  
  val hrefPrefix = "/agent/" + agentID + "/algorithm/" + algID + "/result/"
  
  def toXML: scala.xml.Elem
  
  def postable: (String, String)
  
}

case class StderrResult(agentID: String, algID: String, stderrString:String) extends AlgorithmResult(agentID, algID) {
  
  def toXML: scala.xml.Elem = {
    
    <consoleStderr>
		<href>{hrefPrefix + "console/stderr"}</href>
	</consoleStderr>
	
  }
  
  def postable: (String, String) = ("stderr", stderrString)
  
}

case class StdoutResult(agentID: String, algID: String, stdoutString: String) extends AlgorithmResult(agentID, algID) {
  
  def toXML: scala.xml.Elem = {
    
    <consoleStdout>
		<href>{hrefPrefix + "console/stdout"}</href>
	</consoleStdout>
	
  }
  
  def postable: (String, String) = ("stdout", stdoutString)
  
}

case class FileResult(agentID: String, algID: String, workingDir: String, fileName: String) extends AlgorithmResult(agentID, algID) {
  
  def toXML: scala.xml.Elem = {
    
    <file>
		<href>{hrefPrefix + "file/" + fileName}</href>
	</file>
	
  }
  
  def postable: (String, String) = {
    
    val fileString = Source.fromFile(fileName).toString
    ("file/" + fileName, fileString)
    
  }
  
}

case class AlgorithmResultSet(agentID: String, algID: String, results: AlgorithmResult*) extends AlgorithmResult(agentID, algID) {
  
  def toXML: scala.xml.Elem = {
    
    <results>
		{for(r <- results) yield r.toXML}
	</results>
    
  }
  
  def getStdout = (results find (m => m match {case m: StdoutResult => true; case _ => false} )).get
  def getStderr = (results find (m => m match {case m: StderrResult => true; case _ => false} )).get
  def getFile = (results find (m => m match {case m: FileResult => true; case _ => false} )).get

  def postableResults = results map { _.postable }
  
  // THIS IS CHEATING IT DOESN'T ACTUALLY WORK / SHOULD NOT BE USED!!!!!!
  def postable = results.head.postable
  
}









































