package htrcagent

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

class ExecutableAlgorithm(    
    algoID: String, 
	algoName: String,  	    
	eprMap: HashMap[String,String],
	userArgs: List[String],
	collectionName: String,
	initialDir: String,
	workingDir: String,
	agentRef: ActorRef,
	userID: String
) extends Algorithm (
	algoID, 
	algoName,  	    
	eprMap,
	userArgs,
	collectionName,
	initialDir,
	workingDir,
	agentRef,
	userID
) with Loggable {
  
  val ourRegistry = actorFor[RegistryActor].get

  val runner = ExecutableAlgorithmRunner
    
  def instantiate(): Boolean = {
    
    try {
      //1. Get the algorithm executable
        val algoExecutable: Option[String] = (ourRegistry !! GetAlgorithmExecutable(algoName,workingDir)).asInstanceOf[Option[String]]
        //System.exit(4)
       
      algoExecutable match {
        case None => {
          logger.warn("!!!!> couldnt find algorithm executable for " + algoName)
          agentRef ! UpdateAlgorithmRunStatus(algoID, UnableToFindAlgorithm(algoName, new Date))
        }
        case Some(f) => {
          // continue
          logger.debug("====> we successfully got the algorithm executable: "+algoExecutable)
          doStartAlgorithmWithExecutable(f, workingDir)
        }
        case _ => throw new RuntimeException("wth happened?!")
      }
	    
      def doStartAlgorithmWithExecutable(executable: String, workingDir: String) {
         
	  //2.   get the collection volume IDs
      // note that this could be parallelized against steps 1 and 4!
        // below could be really bad, junk the message passing BS if you need to
         logger.debug("====> Trying to get volume IDs from registry.")
	     val volumeIDs:List[String] = (ourRegistry !!! GetCollectionVolumeIDs(collectionName)).get
         logger.debug("====> Got volume IDs from registry, from this collection: " + collectionName)
         val volumesTextFile = (ourRegistry !!! WriteVolumesTextFile(volumeIDs,workingDir)).get
         logger.debug("====> Wrote volume IDs to a temp file in working dir ("+workingDir+")")
         
      //3.   create the properties file
         //logger.debug("!!!!> FIX ME: volumes should be written out to a vols.txt, and prop file should point there")
         
         
         logger.debug("===createAlgorithmProperties args begin==========================\n\n")
         eprMap.keys.foreach (e => logger.debug("eprMap key: "+e.toString + "\tval: "+eprMap(e)))
         logger.debug("\n")
         logger.debug("userArgs = "+userArgs.toString+"\n")
         logger.debug("first volume ID = " + volumeIDs.head+"\n")
         logger.debug("volumesTextFile = "+volumesTextFile)
         logger.debug("===createAlgorithmProperties args end============================\n\n")
         
         val props = AgentUtils.createAlgorithmProperties(eprMap, 
             userArgs=userArgs, volumeIDs,volumesTextFile )
             
        
          logger.debug("====> Created java props")
        
      
       
         
        
      //5.   let the agent know that we've started running
        agentRef ! UpdateAlgorithmRunStatus(algoID,Running(new Date,workingDir))
        
      //6.   RUN IT!
        
        var outlines:ListBuffer[String] = ListBuffer()
        
          
          
        // we initially did things this way, but the props file ended up
        // containing extraneous backslashes, and it mangled the user arg
        // so that the tab character wasn't written in correctly
//          props.store(new FileOutputStream(new File(workingDir +
//              File.separator + "props.cfg")),"propfile")
        
        // now we use a custom method to write the props file:
        val propFile = new File(workingDir + File.separator + "props.cfg")
        AgentUtils.storePropertiesFileForAlgorithm(props,
	        new BufferedWriter(new FileWriter(propFile)))
        
        
          logger.warn("====> WE ASSUME EXECUTABLE IS A JAVA JAR!")
          logger.debug("====> User directory is "+System.getProperty("user.dir"))
          logger.warn("====> WARNING. Executables are assumed to be java jars!")
          logger.warn("====> WARNING. Collection name is currently hardcoded in RegistryClient.")
          /*outlines = sysCallSimple( 
              List(launchScript,
                  "\"java -jar "+executable+"\"",
                  "props.cfg",workingDir)
              )*/
//          outlines = sysCallSimple(List("\"java -jar "+executable+"\"",
//                  "props.cfg"),workingDir)
//                  
          val MakeAFakeSysCallForTestingPurposes = false
          val sysCallReturn = if (MakeAFakeSysCallForTestingPurposes) {
            // if ls doesn't work... well, that would be annoying...
            //sysCallSimple(List("ls"),workingDir)
            
            // or, call a var-checking script to see a few things
            SysCall.sysCallSimple(List("./check-some-vars.sh"),".")
          } else {
            
             
             SysCall.sysCallSimple(List("java", "-jar", executable,"props.cfg"),workingDir)
          }
        		  
                  
          outlines = sysCallReturn._1
          val exitCode = sysCallReturn._2
          logger.debug("====> output lines = "+outlines.toString())
        
        val outlineString:String = try {
           outlines.reduceLeft( _ + "\n" + _)
        } catch {
          case e:java.lang.UnsupportedOperationException => {
            logger.warn("====> NO OUTPUT LINES from system call")
            ""
          }
          case f => throw f
        }
        if (exitCode == 0) {
          logger.debug("====> Exit code was 0")
          logger.debug("====> Updated run status for algoID "+algoID+" to Finished")
          // begin 2011-09-29 changes
          // gather the algorithm's result set.  this is hardcoded for now
          val fileResults = AgentUtils.findFilesMatchingPattern(pattern="""out-.*txt""",
              dir=workingDir).map((f) => FileResult(workingDir,f.getName()))
          val algoResultSet = new AlgorithmResultSet(StdoutResult(outlineString) :: fileResults)
          agentRef ! UpdateAlgorithmRunStatus(algoID,
            Finished(new Date,workingDir,algoResultSet))
          // post results to registry
          // fix me ... tuple stuff is dumb
          ourRegistry !! PostResultsToRegistry(userID,algoID,algoResultSet.l)
            
        } else {
          logger.warn("!!!!> exit code was " + exitCode)
          logger.debug("====> Updated run status for algoID "+algoID+" to Crashed")
          agentRef ! UpdateAlgorithmRunStatus(algoID,Crashed(new Date,workingDir,new AlgorithmResultSet(List())))
         // end 2011-09-22 changes
        }
	    
      }
      } catch {
          case e => {
            logger.error("!!!!> Couldn't execute algorithm in syscall")
            throw e
          }
        } 
    true
  }
}