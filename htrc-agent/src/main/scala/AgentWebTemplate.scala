/** 
 * Extended for Data to Insight Lab use by Felix Terkhorn, under Apache 2 license
 * 
 * Original copyright Matt Bowen, distributed under the Apache 2 license
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */

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


  object AgentUtils  {
	private val logger = LoggerFactory.getLogger(getClass)
	
	def createWorkingDirectory: String = {
			val algorithmWorkingDirectoryRoot = "htrc-agent-working-dir" 
			val workDirRootFile = new File(algorithmWorkingDirectoryRoot)
			if (!(workDirRootFile.exists())) {
			  logger.debug("try to create top level agent working dir")
			  // we need to create the working directory root
			  if (!workDirRootFile.mkdir()) {
			    // couldn't create it
			    throw new RuntimeException("couldn't create agent working dir top level directory")
			    
			  }
			}
		    val myDir = new File(algorithmWorkingDirectoryRoot + File.separator + UUID.randomUUID().toString())
			if (myDir.mkdir()) {
				logger.debug("====> Successfully created working directory: "+myDir.toString())
				myDir.toString()
			} else {  // couldn't make directory
				throw new RuntimeException("couldn't create agent working directory")
			}
    	
    }
	
	def storePropertiesFileForAlgorithm(props:Properties,
	        fileOut:BufferedWriter) = {
	  try {
	    props.keys().foreach(k => fileOut.write(k+"="+props.get(k)+"\n"))
	    fileOut.close()
	  } catch {
	    case e => { 
	    	logger.error("Couldn't store props file for algorithm")
	    	throw e }
	  }
	}
	
    def fixEscaped(s: String) = {
		// get ready for a headache
		// java requires double backslash to escape a single backslash in string literals
		// futher, regexp lib requires double backslash to escape string literals
		// so to match a *SINGLE* backslash in a java regexp, you need *FOUR* backslashes
		// so, the first part of this expression (replaceAll("\\\\\\\\","\\"))
		//             ... matches double backslash and replaces with a single
		// and the second part of this expression (replaceAll("\\\\:",":"))
		//             ... matches \: and replaces it with :

		s.replaceAll("\\t","\\\\t").replaceAll("\\\\:",":").replaceAll("\\\\\\\\","\\\\")
		// still problematic with this string:
		// replaceEscaped("foo\\\\\\:")
	}
	
	
	def createAlgorithmProperties(eprMap: HashMap[String,String],
                                   userArgs: List[String],
                                   volumeIDs: List[String],
                                   volumeTextFileName: String
                                   ): Properties = {
    val props = new Properties()
    // set it up...
    //Jiaan's simplescript.jar app expects a props file that looks like this:

//  solrEPR = http://coffeetree.cs.indiana.edu:8888/solr
//  cassandraEPR = smoketree.cs.indiana.edu:9160
//  clusterName = Test Cluster
//  keyspaceName = HTRCCorpus
//  volumeListPath = ./FullIUCollection.txt
//  usrArg = w.*\t5
    
    
    val HardcodedArguments:HashMap[String,String] = new HashMap
    HardcodedArguments += "clusterName" -> "Test Cluster"
    HardcodedArguments += "keyspaceName" -> "HTRCCorpus"

    for (keyVal <- HardcodedArguments) props.put(keyVal._1,
        AgentUtils.fixEscaped(keyVal._2))
    for (epr <- eprMap) props.put(epr._1,AgentUtils.fixEscaped(epr._2));
    val UseMultipleUserArgumentsInPropsFile = false
    if (UseMultipleUserArgumentsInPropsFile) { 
      var count = 0
      userArgs.foreach({
    	  	arg => props.put("usrArg%02d".format(count),AgentUtils.fixEscaped(arg))
    	  	count += 1
    	  })
    } else {
      logger.warn("====> WE ONLY USE A SINGLE USER ARGUMENT IN ALGO EXECUTABLE'S PROPS FILE")
      props.put("usrArg",AgentUtils.fixEscaped(userArgs.head))
    }
    
    //props.put("volumes",volumeIDs.reduceLeft(_+","+_)) // you know you love it!
    props.put("volumeListPath",volumeTextFileName)
    logger.debug("====> Successfully created properties object for algorithm executable " +props.toString() )
    props
  }
		 

  
  }

  
  object SysCall {
	private val logger = LoggerFactory.getLogger(getClass)
    def sysCallSimple(commandAndArgs: List[String],workingDir:String) = {
    if (commandAndArgs.length < 1) {
      throw new RuntimeException("sysCallSimple requires a command to be passed, got empty list")
    }
      def helper_runtime_exec = { 
        logger.warn("====> THIS IMPLEMENTATION IS BAD DO NOT USE IT.")
        System.exit(1)
	  
        logger.debug("====> Attempting system call of "+commandAndArgs.toString)
	    //val allargs = args.reduceLeft(_ + " " + _)
	    //logger.debug("====> These are my arguments: "+allargs)
	    val commandArray:Array[String] = new Array(commandAndArgs.length)
        var count = 0
	    commandAndArgs.foreach(s => {
	                commandArray.update(count,s)
	                count = count + 1
	              } )
	    try {
	      val br: BufferedReader = new BufferedReader (
	          new InputStreamReader(
	              Runtime.getRuntime().
	              exec(commandArray).getInputStream()))
	      var outputLines:ListBuffer[String] = ListBuffer() 
	      logger.debug("====> Let's see some output:")
	      while (br.ready()) {
	        val newline = br.readLine()
	        logger.debug("====> SUBPROCESS: Got a new line: "+newline)        
	        outputLines += (newline)
	      }
	      outputLines
	    } catch {
	      case e: Exception => {
	        logger.error("Couldn't read output of algorithm execution")
	        throw e
	      }
	    }
      } // end of helper_runtime_exec def
      
      // try this with java.lang.ProcessBuilder:
      // intuition:
      /*
       * scala> val f = new java.lang.ProcessBuilder ("ls")
f: java.lang.ProcessBuilder = java.lang.ProcessBuilder@93b16c

scala> val sub = f.start()
sub: java.lang.Process = java.lang.UNIXProcess@1d3b70d

scala> sub.waitFor
res13: Int = 0

scala> sub.destroy
        */
       def helper_process_builder = {
        
        logger.debug("====> Attempting system call of "+commandAndArgs.toString)
	    //val allargs = args.reduceLeft(_ + " " + _)
	    //logger.debug("====> These are my arguments: "+allargs)
//	    val commandArray:Array[String] = new Array(commandAndArgs.length)
//        var count = 0
//	    commandAndArgs.foreach(s => {
//	                commandArray.update(count,s)
//	                count = count + 1
//	              } )
	    val myProcessBuilder = try {
	     (new java.lang.ProcessBuilder(commandAndArgs))
	    }
	    catch {
	      case e => { 
	        logger.error("!!!!> couldn't create processbuilder for command: "+commandAndArgs.toString())
	        throw e
	      }
	    }
	    try {
	      myProcessBuilder.directory(new File(workingDir))  
	      
	    } catch {
	      case e => {
	        logger.error("!!!!> couldn't set working directory for subprocess to :"+workingDir)
	        throw e
	      }
	    }
	    val process = try {
	      myProcessBuilder.start()
	    } catch {
	      case e => {
	        logger.error("!!!!> couldn't start subprocess!")
	        throw e
	      }
	    }
        try{
	      val br: BufferedReader = new BufferedReader (
	          new InputStreamReader(
	              process.getInputStream()))
	      var outputLines:ListBuffer[String] = ListBuffer() 
	      logger.debug("====> Let's see some output:")
	      val exitCode = process.waitFor()
	      
	      while (br.ready()) {
	        val newline = br.readLine()
	        logger.debug("====> SUBPROCESS: Got a new line: "+newline)        
	        outputLines += (newline)
	      }
	      (outputLines,exitCode)
	    } catch {
	      case e: Exception => {
	        logger.error("Couldn't read output of algorithm execution")
	        throw e
	      }
	    }
      
       
       }
	    
       helper_process_builder
  }
}
  // ====================
  // ===== Messages =====
  // ====================
 
  //
  // the following define the things the Manager can do
  //
  sealed trait ManagerAction
  case class VendAgent(userID: String, x509: String, privKey: String) extends ManagerAction // org.cilogon.portal.util._
  // have the agent call its preStart method to get data from registry
  case class TakeAction(agentID: String, action: AgentAction) extends ManagerAction
  case object ListAgents extends ManagerAction
  case class DestroyAgent(id: String) extends ManagerAction
  
  //
  // the following define things that an Agent can do
  //
  sealed trait AgentAction
  case class RunAlgorithm(algorithmName: String, collectionName: String, arguments: List[String]) extends AgentAction
  case object ListCollections extends AgentAction
  case object GetIndexEpr extends AgentAction
  case object GetRepositoryEpr extends AgentAction
  case object GetRegistryEpr extends AgentAction  // read this from props file so that we can bootstrap
  case object ListAvailableAlgorithms extends AgentAction
  case object ListCurrentAlgorithms extends AgentAction
  case class ListCurrentAlgorithms(status: AlgorithmRunStatus)
  case class StoreToRegistry(resourceType: String, resourceObject: Any) extends AgentAction
  case class PollAlgorithmRunStatus(algoID: String) extends AgentAction
  //utility messages... less interesting:
  case object GetCredentials extends AgentAction
  case object GetAgentID extends AgentAction
  case object GetAgentIDAsString extends AgentAction
  case object GetUserIDAsString extends AgentAction
  case class UpdateAlgorithmRunStatus(algoID: String, status: AlgorithmRunStatus)

  
  //
  // third class of Actor -- ones that run algorithms on behalf of an agent
  // by the time this Actor is invoked, the Agent should have assigned this run
  // of the given algorithm a unique ID string
  sealed trait AgentSlaveAction 
  case class StartAlgorithm(algoID: String, 
		  				    algoName: String, 
		  				    eprMap: HashMap[String,String],
		  				    userArgs: List[String],
		  				    collectionName: String) extends AgentSlaveAction
  case class GetCollectionVolumeIDs(collectionName: String) extends AgentSlaveAction
  case object SlaveListCollections extends AgentSlaveAction
  case object SlaveListAvailableAlgorithms extends AgentSlaveAction
  
  
  //
  // the following define possible statuses of an algorithm
  //
  sealed trait AlgorithmRunStatus
  case class Prestart(time: Date) extends AlgorithmRunStatus
  case class Initializing(time: Date) extends AlgorithmRunStatus
  case class Running(time: Date, workingDir: String) extends AlgorithmRunStatus
  // begin 2011-09-22 changes 
  // we want to carry algorithm results around in the status
  case class Finished(time: Date, workingDir: String, algorithmResults:AlgorithmResultSet) extends AlgorithmRunStatus
  case class Crashed(time: Date, workingDir: String, algorithmResults:AlgorithmResultSet) extends AlgorithmRunStatus
  // renamed the following class
  case class UnableToFindAlgorithm(algoName: String,time: Date) extends AlgorithmRunStatus
  
  // the following classes encapsulate possible results from an algorithm with
  // Finished or Crashed status
  // right now we only deal with stdout, stderr, and file results
  abstract class AlgorithmResult
  case class StderrResult(stderrString:String) extends AlgorithmResult
  case class StdoutResult(stdoutString:String) extends AlgorithmResult
  case class FileResult(workingDir:String,fileName:String) extends AlgorithmResult
  case class AlgorithmResultSet(l:List[AlgorithmResult]) 
  // end 2011-09-22 changes 
  
  
//class CachedEPR(name: String,initialEpr: URI, refreshEpr: (() => URI), 
//                expirationTime: Int = 3600000 /*milliseconds*/) {
//  // pass this class an EPR and a function to update the EPR after a given delay
//  var lastAccess = new Date
//  var epr = initialEpr
//  def getEPR: URI = {
//    val now = new Date
//    if (now.getTime - lastAccess.getTime > expirationTime) {
//      epr = refreshEpr()
//      epr
//    } else {
//      epr
//    }
//  }
//}
//  
class CachedEPR(name: String, refreshEpr: (() => URI),
    expirationTime: Int = 3600000 /*milliseconds*/ ) {
    private val logger = LoggerFactory.getLogger(getClass)
    // pass this class a function to update the EPR after a given delay
    private var lastAccess = new Date((new Date).getTime - 900000000) // this will be rewritten anyway
    private var epr:Option[URI] = None
    def getEPR: URI = {
    		def checkTimeAndReturn = {
    				val now = new Date			
    				if (now.getTime - lastAccess.getTime > expirationTime) {
    				    logger.debug("refreshing EPR, last access is old")
    					epr = Some(refreshEpr())
    				}
    				lastAccess =new Date
    				epr.head
    		}
    		
    		def helper: URI = {
    		  lastAccess = new Date
    		  epr = Some(refreshEpr())
    		  epr.head
    		}
    		
    		epr match {
    			case None => {logger.debug("no cached EPR found");helper}
    			case Some(uri) => checkTimeAndReturn
    		}
    }
    
    

  }
  
class AgentSlave(agentRef: ActorRef, registryClient: RegistryClient, 
    userID: String, x509: String, privKey: String,runtimeProps: Properties) extends Actor {
  private val changeUserDirOnAlgoFetch = false
  private val copyAlgoJarToWorkingDir = true
  private val launchScript:String = runtimeProps.get("algolaunchscript").toString()
  private val logger = LoggerFactory.getLogger(getClass) 
  private def getAlgorithmExecutable(algoName: String, 
      workingDir: String): Option[String] = {
    def getAlgoFromRegistryHelper = {
    // this method should contact the registry and return a file(executable)
    val jarFile:String = try {
      if (changeUserDirOnAlgoFetch) {
      logger.warn("====> VERY DANGEROUS! SETTING JAVA USER.DIR PROPERTY")
      logger.warn("====> VERY DANGEROUS! SETTING JAVA USER.DIR PROPERTY")
      logger.warn("====> VERY DANGEROUS! SETTING JAVA USER.DIR PROPERTY")
      val initialDir = System.getProperty("user.dir")
      System.setProperty("user.dir",workingDir)
      val temp = registryClient.getScriptFile(algoName)
      System.setProperty("user.dir",initialDir)
      temp
      } else {
        registryClient.getScriptFile(algoName)
      }
    } catch {
      case npe: NullPointerException => {
        logger.warn("====> downloadFile in RegistryClient threw an NPE, recovering...")
        null
      }
      case e => throw e
    }
    if (jarFile == null) {
      logger.warn("====> Couldn't find algorithm executable in registry, for "+algoName)
      None
    } else {
      logger.debug("====> Successfully found algorithm executable in registry: "+jarFile)
      //val f = new File(workingDir + File.separator + jarFile)
      //val f = new File(jarFile)
      
      Some(jarFile)
    }
    }
    
    def getAlgoButUseLocalCopySinceRegistryIsBroken = {     
      
      // this directory is relative to the project root
      // we'll use it to store executables just in case the registry fails
      val EmergencyAlgorithmExecutableStorageDirectory = 
        "emergency-algorithm-executable-storage"
      
      val extensions = List("",".jar")
      var theFileWeNeed:File = null 
      extensions foreach  
        	(e => { val f = (new File(EmergencyAlgorithmExecutableStorageDirectory +
        			File.separator + algoName + e))
        			logger.debug("====> look for this filename: "+f.toString)
        	       if (f.exists() && f.canRead())
        	         theFileWeNeed = f}) 
      if  ( theFileWeNeed == null ) {
    	  logger.error("!!!!> Couldn't find a copy of the file we need in the emergency storage space")
    	  None
      } else {
        logger.debug("====> we are returning this copy of the executable from "+
    	      "emergency file storage: "+theFileWeNeed.toString)
    	  Some(theFileWeNeed.toString)
      } 
    }
    val SkipGettingAlgoExecutableFromRegistry = {
       val skipProp = runtimeProps.get("skipgettingalgoexecutablefromregistry")
       logger.debug("====> runtime property of 'skipgettingalgoexecutablefromregistry' is "+
           skipProp.toString)
	  
        skipProp match {
         case "true" => true
         case "1" => true
//	     case Some("true") => true
//	     case Some("1") => true
	     case _ => false	     
	   }
       
	  }
    val optionFileNameString:Option[String] = if (!SkipGettingAlgoExecutableFromRegistry) {
        getAlgoFromRegistryHelper
      } else {
        // due to bug documented here[1], i can't post algorithm executable
        // to registry.  use a local copy instead
        // [1] http://wso2.org/forum/thread/14971
        logger.warn("===> We'll skip getting the algorithm executable from registry")
        getAlgoButUseLocalCopySinceRegistryIsBroken
      }
    
    if (optionFileNameString != None) {
    	// trim the leading directory name from the file
    	val filenameOnly = optionFileNameString.head.split(File.separator).last
    			if (copyAlgoJarToWorkingDir) {
    				logger.warn("====> DANGEROUS ... COPYING ALGO FILE TO WORKING DIR")
    				FileUtils.copyFile(new File(optionFileNameString.head),
    						new File(workingDir + File.separator + filenameOnly))
    			}
    	Some(filenameOnly)
    } else 
    	None
  }
  
  
  
  private def getCollectionVolumeIDs(collectionName:String) = {
    // new changes 2011-09-23
    logger.debug("!!!!> inside getCollectionVolumeIDs, asked for "+collectionName)
    val volumeIDs:java.util.List[String] = registryClient.
                 getVolumeIDsFromCollection(collectionName)
    logger.debug("!!!!> got the list of volumes, size is"+volumeIDs.toList.length)
    volumeIDs.toList
    // end changes 2011-09-23
    
  }
  
  
  
  
  def writeVolumesTextFile (volumes:List[String],workingDir: String):String = {
    val volumeTextFileName = "vols-"+UUID.randomUUID().toString()+".txt"
    val outstream = new FileWriter(workingDir + File.separator +
        volumeTextFileName)
    val  out = new BufferedWriter(outstream)
    volumes.foreach((v => out.write(v+"\n")))
    out.close()
    volumeTextFileName
  }
  
  private def listCollections = {
    def listHardcodedCollections = {
      val listOfCollections = try {
        runtimeProps.get("hardcodedcollectionnames").toString.split(",")
      } catch {
        case e => {
          logger.error("couldn't get hardcoded collection names")
          throw e 
        }
      }
      listOfCollections.toList
    }
    
//    var collectionsRootNode = <collections/>.toList.head
//    listHardcodedCollections.foreach (c => {
//      collectionsRootNode = XmlUtil.addNode(collectionsRootNode,
//          <collection>{scala.xml.Text(c)}</collection>)
//    })
//    collectionsRootNode
    
    <collections>
      {for (c <- listHardcodedCollections) yield <collection>{scala.xml.Text(c)}</collection>}
    </collections>
//    
    
  }
  
  private def listAvailableAlgorithms = {
    def listHardcodedAlgorithms = {
      val listOfAlgorithms = try {
        runtimeProps.get("hardcodedalgorithmsavailable").toString.split(",")
      } catch {
        case e => {
          logger.error("couldn't get hardcoded available algorithm names")
          throw e 
        }
      }
      listOfAlgorithms.toList
    }
    
//    var availableAlgorithmsRootNode = <availableAlgorithms/>.toList.head
//    listHardcodedAlgorithms.foreach (c => {
//      availableAlgorithmsRootNode = XmlUtil.addNode(availableAlgorithmsRootNode,
//          <algorithm>{scala.xml.Text(c)}</algorithm>)
//    })
//    availableAlgorithmsRootNode
//    
    <availableAlgorithms>
      {for (a <- listHardcodedAlgorithms) yield <algorithm>{scala.xml.Text(a)}</algorithm>}
     </availableAlgorithms>
  }
  
  def receive = {
    case SlaveListAvailableAlgorithms => {
      logger.debug("INSIDE AGENTSLAVE SlaveListAvailableAlgorithms")
      self reply listAvailableAlgorithms
    }
    case SlaveListCollections => {
      logger.debug("INSIDE AGENTSLAVE SlaveListCollections")
      self reply listCollections
    }
    case StartAlgorithm(algoID: String, 
		  				    algoName: String, 
		  				    eprMap: HashMap[String,String],
		  				    userArgs: List[String],
		  				    collectionName: String) => {
	  
	  logger.debug("====> AgentSlave received StartAlgorithm message")
	  //0. Let the agent know that we're initializing the algorithm
	  agentRef ! UpdateAlgorithmRunStatus(algoID,Initializing(new Date))	
	  
      logger.debug("====> Check 1...")
      //0.5.   create the working directory and change to it 
      //     WARNING -- CHANGING TO THAT DIRECTORY WILL SCREW UP
      //     CONCURRENTLY RUNNING ACTORS.  FIGURE THIS OUT.
      // MAKE THIS ATOMIC?
      logger.warn("====> Need to make directory change section atomic!")
      val initialDir = System.getProperty("user.dir")
      val workingDir = AgentUtils.createWorkingDirectory
      
      try {
      //1. Get the algorithm executable
        val algoExecutable =getAlgorithmExecutable(algoName,workingDir)
        //System.exit(4)
       
      algoExecutable match {
        case None => {
          logger.warn("!!!!> couldnt find algorithm executable for " + algoName)
          agentRef ! UpdateAlgorithmRunStatus(algoID,
            // begin 2011-09-22 changes 
            UnableToFindAlgorithm(algoName, new Date))
            // end 2011-09-22 changes 
        }
        case Some(f) => {
          // continue
          logger.debug("====> we successfully got the algorithm executable: "+algoExecutable)
          doStartAlgorithmWithExecutable(f,workingDir,launchScript)
        }
        case _ => throw new RuntimeException("wth happened?!")
      }
	    
      def doStartAlgorithmWithExecutable(executable: String,
          workingDir: String,launchScript:String) {
         
	  //2.   get the collection volume IDs
      // note that this could be parallelized against steps 1 and 4!
        // below could be really bad, junk the message passing BS if you need to
         logger.debug("====> Trying to get volume IDs from registry.")
	     val volumeIDs:List[String] = getCollectionVolumeIDs(collectionName)
         logger.debug("====> Got volume IDs from registry, from this collection: " + collectionName)
         val volumesTextFile = writeVolumesTextFile(volumeIDs,workingDir)
         logger.debug("====> Wrote volume IDs to a temp file in working dir ("+workingDir+")")
         logger.debug("====> we will use launch script: "+launchScript)
         logger.debug("====> launch script exists? " +(new File(launchScript)).exists())
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
          if (changeUserDirOnAlgoFetch) {
        	  logger.warn("====> VERY DANGEROUS! ALGO FETCH SETTING JAVA USER.DIR PROPERTY")
        	  logger.warn("====> VERY DANGEROUS! ALGO FETCH SETTING JAVA USER.DIR PROPERTY")
        	  logger.warn("====> VERY DANGEROUS! ALGO FETCH SETTING JAVA USER.DIR PROPERTY")
          }
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
          // begin 2011-09-22 changes
          // trying to abstract over stdout console vs stderr console vs file result
        agentRef ! UpdateAlgorithmRunStatus(algoID,
            Finished(new Date,workingDir,new AlgorithmResultSet(List(StdoutResult(outlineString)))))
          
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
      
      
    }
    case GetCollectionVolumeIDs(collectionName: String) => {
      self reply getCollectionVolumeIDs(collectionName)
    }
      
    case _ => self reply <error>Unknown algorithm control message</error>
  }
}
  
  
class Agent(userID: String,x509: String,privKey: String,
    runtimeProps:Properties) extends Actor  {
  private val logger = LoggerFactory.getLogger(getClass)
  val registryClient = new RegistryClient
  val algorithmRunStatusMap = new HashMap[String,AlgorithmRunStatus]
                          //e.g.   huetoanhu-4731sssa-fueoauht, 'Initializing                                  
		     			  // 	   huetoanhu-4731sssa-fueoauht, 'Running
                          //       huetoanhu-4731sssa-fueoauht, 'Finished
  private def generateAlgorithmRunID: String = UUID.randomUUID().toString
  private def updateAlgorithmRunStatus(algoID: String, status: AlgorithmRunStatus) = {
    logger.debug("algorithmRunStatusMap before put of "+(algoID,status)+": "+algorithmRunStatusMap)
    algorithmRunStatusMap.put(algoID,status)
    logger.debug("algorithmRunStatusMap after put of "+(algoID,status)+": "+algorithmRunStatusMap)
  }
  private def getAlgorithmRunStatus(algoID:String): Option[AlgorithmRunStatus] = algorithmRunStatusMap.get(algoID)
  
  //we now use the URIName of x509 cert to set the agent's ID
  //val agentID = UUID.randomUUID()
  val agentID = userID
  
  //val cachedIndexEPR = new CachedEPR("index-epr",refreshEpr=refreshIndexEPR )
  
  val refreshIndexEPR = {() =>  
    //println("RegistryClient object:"+registryClient.toString())      
    new URI(registryClient.getSolrIndexServiceURI("htrc-apache-solr-search"))
  }
  val refreshRepositoryEPR = {() =>    
    //println("RegistryClient object:"+registryClient.toString())      
    new URI(registryClient.getSolrIndexServiceURI("htrc-cassandra-repository"))
  }
  private def getAgentID = agentID
  private def credentialsToXml = {
    <credentials>
        <x509certificate>{x509}</x509certificate>
        <privatekey>{privKey}</privatekey>
    </credentials>
  }
  private def toXml: scala.xml.Elem = {
    <agent>
      <agentID>{getAgentID}</agentID>
	  <userID>{userID}</userID>
      {credentialsToXml}
	</agent>
  }
  
  def generateServiceEPRMap: HashMap[String,String] = {
    val map = new HashMap[String,String]
    
    // get the values... we'll do this in as  simple  a manner as possible for now
    logger.debug("    ====> This agent is trying to get index EPR from itself")
    // below three didn't work At All !

    val indexEpr = getIndexEPR()
    val repositoryEpr = getRepositoryEPR()
    val registryEpr = getRegistryEPR()

    
    // Jiaan HTRCApp expects this format
//  solrEPR = http://coffeetree.cs.indiana.edu:8888/solr
//  cassandraEPR = smoketree.cs.indiana.edu:9160
//  clusterName = Test Cluster
//  keyspaceName = HTRCCorpus
//  volumeListPath = ./FullIUCollection.txt
//  usrArg = w.*\t5    
    
    map.put("solrEPR",indexEpr)
    map.put("cassandraEPR",repositoryEpr)
    map.put("registryEPR",registryEpr)
    
    map
  }
  
  // begin 2011-09-22 changes 
  
  // documentation: a call to list all running algorithms results in this xml output:
  // <algorithmRunList>
  //        <!-- first <algorithmRun> element -- see below for individual algorithmRun details -->
  //        <!-- next <algorithmRun> element, &c. -->
  // </algorithmRunList>
  
  // documentation: this is what an individual algorithm run status should look like
  // i'm including examples for every type of status
  /*
   * -- prestart:
   * 
   * <algorithmRun>
   *    <id>accf40e4-d15e-490a-a2d0-e9d1ec248ce0</id>
   *    <status>Prestart</status>
   *    <lastStatusChange>Thu Sep 22 07:57:15 EDT 2011</lastStatusChange>
   * </algorithmRun>
   * 
   * -- initializing:
   * 
   * <algorithmRun>
   *    <id>accf40e4-d15e-490a-a2d0-e9d1ec248ce0</id>
   *    <status>Initializing</status>
   *    <lastStatusChange>Thu Sep 22 07:57:15 EDT 2011</lastStatusChange>
   * </algorithmRun>
   * 
   * -- running:
   * 
   * <algorithmRun>
   *    <id>accf40e4-d15e-490a-a2d0-e9d1ec248ce0</id>
   *    <status>Running</status>
   *    <lastStatusChange>Thu Sep 22 07:57:15 EDT 2011</lastStatusChange>
   *    <workingDir>f30584e0-95c4-470e-a133-6b9a59aa576c</workingDir>
   * </algorithmRun>
   * 
   * -- crashed:
   * 
   * <algorithmRun>
   *    <id>accf40e4-d15e-490a-a2d0-e9d1ec248ce0</id>
   *    <status>Crashed</status>
   *    <lastStatusChange>Thu Sep 22 07:57:15 EDT 2011</lastStatusChange>
   *    <workingDir>f30584e0-95c4-470e-a133-6b9a59aa576c</workingDir>
   *    <results>
   *       <consoleStdout>
   *         <href>/agent/{agentURN}/algorithm/result/console/stdout</href>
   *       </consoleStdout>
   *       <consoleStderr>
   *         <href>/agent/{agentURN}/algorithm/result/console/stderr</href>
   *       </consoleStderr>
   *       <file>
   *         <href>/agent/{agentURN}/algorithm/result/file/exampleOutputFile.txt</href>
   *       </file>
   *       <file>
   *         <href>/agent/{agentURN}/algorithm/result/file/exampleOutputFileAnother.txt</href>
   *       </file>
   *    </results>
   * </algorithmRun>
   * 
   * -- finished:
   * 
   * <algorithmRun>
   *    <id>accf40e4-d15e-490a-a2d0-e9d1ec248ce0</id>
   *    <status>Finished</status>
   *    <lastStatusChange>Thu Sep 22 07:57:15 EDT 2011</lastStatusChange>
   *    <workingDir>f30584e0-95c4-470e-a133-6b9a59aa576c</workingDir>
   *    <results>
   *       <consoleStdout>
   *         <href>/agent/{agentURN}/algorithm/result/console/stdout</href>
   *       </consoleStdout>
   *       <consoleStderr>
   *         <href>/agent/{agentURN}/algorithm/result/console/stderr</href>
   *       </consoleStderr>
   *       <file>
   *         <href>/agent/{agentURN}/algorithm/result/file/exampleOutputFile.txt</href>
   *       </file>
   *       <file>
   *         <href>/agent/{agentURN}/algorithm/result/file/exampleOutputFileAnother.txt</href>
   *       </file>
   *    </results>
   * </algorithmRun>
   * 
   * -- unable to find executable:
   * 
   * <algorithmRun>
   *    <id>accf40e4-d15e-490a-a2d0-e9d1ec248ce0</id>
   *    <status>UnableToFindAlgorithm</status>
   *    <lastStatusChange>Thu Sep 22 07:57:15 EDT 2011</lastStatusChange>
   *    <algorithmName>foobar.jar</algorithmName>
   * </algorithmRun>
   * 
   * 
   */
  private def algorithmRunExists(algoID:String) = {
    algorithmRunStatusMap.contains(algoID)
  }
  
  private def listAllAlgorithmRunIDs = {
    logger.debug("===> what keys are inside the algorithm run status map ? "+algorithmRunStatusMap.keys)
    for (k <- algorithmRunStatusMap.keys) yield k
  }
  
  private def formAlgorithmRunXMLResponse(algoID: String) = {
//     def runningResponseHelper = {
//       
//     }
//     def finishedResponseHelper = {
//       
//     }
//     def crashedResponseHelper = {
//       
//     }
//     
     def renderAlgorithmResultSetXML(algoResultSet:AlgorithmResultSet) = {
       val hrefPrefix = "/agent/"+agentID+"/algorithm/"+algoID+"+/result/"
       <results>
        {for (r <- algoResultSet.l) yield 
          r match {
           case StdoutResult(s) =>  {
             <consoleStdout><href>{hrefPrefix+"console/stdout"}</href></consoleStdout>
           }
           case StderrResult(s) => {
             <consoleStderr><href>{hrefPrefix+"console/stderr"}</href></consoleStderr>
           }
           case FileResult(wrkDir,fName) => {
             <file><href>{hrefPrefix+"file/"+fName}</href></file>
           }
        }
        }
       </results>
       
     }
     
     def unableToFindAlgorithmResponseHelper(n:String) = {
       <algorithmName>
         {n}
       </algorithmName>
     }
     // determine whether that algorithm exists
      if (algorithmRunExists(algoID)) {
        // if it does, render it
        // following is common to all
        <algorithmRun>
          <id>{ algoID }</id>
         { for ( element <-  getAlgorithmRunStatus(algoID).head match {          
            case Prestart(d) => { List(
              <status>Prestart</status>,
              <lastStatusChange> {d} </lastStatusChange>)
            }
            case Initializing(d) => {
             List( <status>Initializing</status>,
              <lastStatusChange> {d} </lastStatusChange>)
            }
            case Running(d,workingDir) => {
              List(<status>Running</status>,
              <lastStatusChange> {d} </lastStatusChange>,
              <workingDir>{workingDir}</workingDir>)
            }
            case Finished(d,workingDir:String,algorithmResults:AlgorithmResultSet) => {
              List(
                <status>Finished</status>,
                <lastStatusChange> {d} </lastStatusChange>,
                <workingDir>{workingDir}</workingDir>,
                {renderAlgorithmResultSetXML(algorithmResults)}
                )
                
            }
            case Crashed(d,workingDir:String,algorithmResults:AlgorithmResultSet) => {
               List( <status>Finished</status>,
                <lastStatusChange> {d} </lastStatusChange>,
                <workingDir>{workingDir}</workingDir>,
                {renderAlgorithmResultSetXML(algorithmResults)})
            }
            case UnableToFindAlgorithm(algoName,time) => {
              List(<status>UnableToFindAlgorithm</status>,
                  <lastStatusChange>{time}</lastStatusChange>,
                  <algorithmName>{algoName}</algorithmName>)
            }
          }) yield element }
         </algorithmRun>
           // above is a bit abstruse
           // but just trust me that it conforms to the documented form of the xml ;)
      } else {
        // error response
        throw new RuntimeException("no such algorithm "+algoID+ " for agent "+agentID)
      }
    
  }
  
  private def formAlgorithmRunListXMLResponse() = {
    //logger.debug("===> listAllAlgorithmRunIDs: "+listAllAlgorithmRunIDs)
    <algorithmRunList>
	  {for (a <- listAllAlgorithmRunIDs) yield formAlgorithmRunXMLResponse(a)}
    </algorithmRunList>
  }
  
  // end 2011-09-22 changes
  
  
  private def getIndexEPR():String = refreshIndexEPR().toString()
  private def getRepositoryEPR():String = refreshRepositoryEPR().toString()
  private def getRegistryEPR():String = { logger.warn("====> fix fake response to getRegistryEPR()");"greetings."}
  
  // create the workers
  val nrOfSlaves = 8
  val slaves = Vector.fill(nrOfSlaves)(actorOf
		 (new AgentSlave(self,registryClient,
		     userID, x509, privKey,runtimeProps)).start())

  // wrap them with a load-balancing router
  val router = Routing.loadBalancerActor(CyclicIterator(slaves)).start()

  
  def receive = {
    case GetUserIDAsString => self.reply(userID)
    case GetAgentID => self.reply(<agentID>{getAgentID}</agentID>)
    case GetAgentIDAsString => self.reply(getAgentID)
    case GetIndexEpr => {  
     self reply <index>{getIndexEPR()}</index>
     // caching EPRs is still broken
     //self.reply(<index>{cachedIndexEPR.getEPR.toString()}</index>)
    }
    case ListAvailableAlgorithms => {
      logger.debug("INSIDE **AGENT** ListAvailableAlgorithms")
      self reply ( router !! SlaveListAvailableAlgorithms).getOrElse( <error>couldn't list available algorithms</error>)
    }
    case ListCollections => {
      logger.debug("INSIDE **AGENT** ListAvailableAlgorithms")
      self reply (router !! SlaveListCollections).getOrElse(<error>Couldn't list collections</error>) 
    }
    case GetRegistryEpr => self.reply(<registry>{getRegistryEPR()}</registry>)
    case GetRepositoryEpr => self.reply(<repository>{getRepositoryEPR()}</repository>) // needs .toString ?
    case GetCredentials=> self reply credentialsToXml
    case UpdateAlgorithmRunStatus(algoID: String,status: AlgorithmRunStatus) => {
      status match {
        case s: Prestart => updateAlgorithmRunStatus(algoID,s)
        case s: Initializing => updateAlgorithmRunStatus(algoID,s)
        case s: Running => updateAlgorithmRunStatus(algoID,s)
        case s: Finished => {
          logger.info("====> ATTENTION <====\nThis is the output:\n"+s+
              "=====================\n")
          updateAlgorithmRunStatus(algoID,s)
        }
        case s: Crashed => updateAlgorithmRunStatus(algoID,s)
        // begin 2011-09-22 changes 
        case s: UnableToFindAlgorithm => updateAlgorithmRunStatus(algoID,s)
        // end 2011-09-22 changes 
        case _ => throw new RuntimeException("unknown algorithm run status!")
      }
    }
    case PollAlgorithmRunStatus(algoID: String) => {
       self reply formAlgorithmRunXMLResponse(algoID)
    }
    case ListCurrentAlgorithms => {
      self reply formAlgorithmRunListXMLResponse()
    }
    case RunAlgorithm(algorithmName: String, collectionName: String, userArguments: List[String]) => {
      logger.debug("====> We're in RunAlgorithm case of Agent's receive block")
      // we need to make sure we have a hashmap containing IndexEPR, RepositoryEPR, and RegistryEPR
      // eg. "index" -> "http://coffeetree:9992/solr"
      // etc.
      val eprMap:HashMap[String,String] = generateServiceEPRMap
      logger.debug("    ====> Generated service epr map")
      // we then generate an algorithRunID which will be used to check up on this algorithm run
      val algoID = generateAlgorithmRunID
      logger.debug("    ====> Generated algorithm run ID: "+algoID)
      // parallelize this when you get a chance
      //val volumeIDs = slaveRouter !! GetCollectionVolumeIDs(collectionName)
      
      // update the run status to prestart -- nothing has happened yet, but we have an ID for the algorithm
      updateAlgorithmRunStatus(algoID,Prestart(new Date))
      logger.debug("    ====> Updated algorithm run status to Prestart")

      logger.debug("    ====> Telling a slave to start the algorithm "+algoID+" '%s'".format(algorithmName))
      router ! StartAlgorithm(algoID,
        algorithmName,
        eprMap,
        userArguments,
        collectionName)
        
      self reply formAlgorithmRunXMLResponse(algoID)
        
      
    }
    case _ => self.reply(<error>Invalid action</error>)
  }
}



class Manager extends Actor {
  private val logger = LoggerFactory.getLogger(getClass)
  private val agentMap = new HashMap[String,ActorRef]
  private val ConfFile = "config.properties"
  private val runtimeProps = new Properties
  runtimeProps.load(new FileInputStream(ConfFile))
  def checkForAgentByUserID(userID: String): Boolean = {
    agentMap.values.exists((a => (a !! GetUserIDAsString) == userID))
  }
  
  /*def getAgentIDByUserID(userID: String): String = {
    // write me
    //(agentMap.values.filter((a => (a !! GetAgentIDAsString) == userID))) !! GetAgentIDAsString
  }*/
  
  def getAgent(id:String) = agentMap.get(id)
  
  def validCredentials(id: String, x509: String, privKey: String): Boolean = { true  } // always succeed, for now
  
  def agentExists(agentID: String): Boolean = agentMap.contains(agentID)
  
  def receive = {
    case VendAgent(uriName, x509, privKey) => {
      //logger.debug("Vending agent for id: %s",id)
      if (validCredentials(uriName,x509,privKey)) // always succeeds :\
        // begin 2011-09-22 changes
    	  // we were making a new agent on every call to the rest PUT method for creating agents
    	  // :(
         { 
           getAgent(uriName) match {
             case None => {  
           
            	 val newAgent = actorOf(new Agent(uriName,x509,privKey,runtimeProps)).start()
            			 val newAgentID = (newAgent !! GetAgentIDAsString).getOrElse("Failed")
            			 if (newAgentID == "Failed") throw new RuntimeException
            			 
            			 logger.debug("===> putting a new agent to the agent map")
            			 agentMap.put(newAgentID.toString(),newAgent)
            			 // end 2011-09-22 changes
            			 self reply <agentID>{newAgentID}</agentID>
             }
             case Some(agentActorRef) => {
               logger.debug("===> an agent already existed for this uriName: "+uriName)
               self reply <agentID>{uriName}</agentID>
             }
           }
             // end 2011-09-22 changes
         } else if (checkForAgentByUserID(uriName)) {
           //self reply <agentID>{getAgentIDByUserID}</agentID>
           self reply <error>Implement me</error>
         }
         else { 
           // invalid Credentials
           self reply <error>Vending agent failed</error>
         }
    }
    case ListAgents => {
      self reply 
         <agents>
              {for (agent <- agentMap.keys) yield <agent>{agent}</agent>}
         </agents> 
    }
    case TakeAction(agentID: String, action: AgentAction) => {
      // does this agent exist?
      if (agentExists(agentID)) {
        val result = (getAgent(agentID).head !! action).getOrElse(<error>Couldn't resolve action</error>)
        self reply result
      } else {
        self reply <error>No such agent</error> // should be a 404 not found
      }
      
    }
    case _ => self reply <error>This is not a valid command</error>
  }
}



@Path("/agent/{agentID}/algorithm/poll")
class AgentListCurrentAlgorithms {
  // list all algorithms attempted during this agent's session
  // can match any status in AlgorithmRunStatus
  // need this for the "check algorithm status" page in HTRC Portal
  private val logger = LoggerFactory.getLogger(getClass)
  @GET
  @Produces(Array("text/xml"))
  def listCurrentSessionAlgorithms(@PathParam("agentID") agentID:String) = {
    val manager = actorsFor(classOf[Manager]).headOption.get
    (manager !! TakeAction(agentID,
        ListCurrentAlgorithms)).getOrElse("couldn't list current algorithms")
  }
}

// i think the below would just be a duplicate of the above
// this would be nice to have but it's not critical right now
//@Path("/agent/{agentID}/algorithm/list/current/{status}")
//class AgentListCurrentlgorithmsWithStatusArg {
//  // list all algorithms attempted during this agent's session
//  // parameterize by a given status (eg "running")
//  private val logger = LoggerFactory.getLogger(getClass)
//  private def convertStringToAlgorithmRunStatusType(status:String):AlgorithmRunStatus = {
//    status match {
//      case "running" => Running
//      case "initializing" => Initializing
//      case "finished" => Finished
//      case "prestart" => Prestart
//      case "crashed" => Crashed
//      case "unabletofindexecutable" => UnableToFindExecutable
//      case _ => Running
//    }
//  }
//  @GET
//  @Produces(Array("text/xml"))
//  def listCurrentSessionAlgorithmsWithArg(@PathParam("agentID") agentID:String,
//                                          @PathParam("status") status:String
//      ) = {
//  val askedStatus = convertStringToAlgorithmRunStatusType(status)
//  val manager = actorsFor(classOf[Manager]).headOption.get
//    (manager !! TakeAction(agentID,
//        ListCurrentAlgorithms(askedStatus)))
//  }
//}
//   
//}
//
@Path("/agent/{agentID}/algorithm/poll/{algoID}")
class AgentPollAlgorithmRunStatusService {
  private val logger = LoggerFactory.getLogger(getClass)
  @GET
  @Produces(Array("text/xml"))
  def getIndexEpr(@PathParam("agentID") agentID:String,
                  @PathParam("algoID") algoID: String) = {
    val manager = actorsFor(classOf[Manager]).headOption.get
    (manager !! TakeAction(agentID, 
                     PollAlgorithmRunStatus(algoID))).
                     getOrElse("Couldn't get algorithm run status for algoID "
                         + algoID + " against agent " +agentID)
  
  }
}

@Path("/agent/{agentID}/algorithm/run/{algoName}/{collectionName}/{userArgs}")
class AgentRunAlgorithmService {
  private val logger = LoggerFactory.getLogger(getClass)
  @GET
  @Produces(Array("text/xml"))
  def getIndexEpr(@PathParam("agentID") agentID:String,
                  @PathParam("algoName") algoName: String,
                  @PathParam("collectionName") collectionName: String,
                  @PathParam("userArgs") userArgs: String) = {
    val manager = actorsFor(classOf[Manager]).headOption.get
    val userArgList=List(userArgs)
    logger.warn("====> REST Endpoint for RunAlgorithm can only accept a single argument -- fix this!")
    (manager !! TakeAction(agentID, 
                     RunAlgorithm(algoName,collectionName,userArgList))).
                     getOrElse("Couldn't get repository EPR from agent "+agentID)
  }
}

// need this for HTRC portal "run an algorithm" form; result populates the widget
@Path("/agent/{agentID}/algorithm/list")
class AgentListAvailablelgorithms {
  // list all algorithms available to run
  // this will pull the algorithms available from the Registry
  //private val logger = LoggerFactory.getLogger(getClass)
  @GET
  @Produces(Array("text/xml"))
  def listRunningAlgorithms(@PathParam("agentID") agentID:String) = {
    val manager = actorsFor(classOf[Manager]).headOption.get
    (manager !! TakeAction(agentID,
        ListAvailableAlgorithms)).getOrElse("couldn't find available algorithms")
  }
}

@Path("/agent/{agentID}/repository-epr")
class AgentGetRepositoryEPRService {
  @GET
  @Produces(Array("text/xml"))
  def getIndexEpr(@PathParam("agentID") agentID:String) = {
    val manager = actorsFor(classOf[Manager]).headOption.get
    (manager !! TakeAction(agentID, GetRepositoryEpr)).getOrElse("Couldn't get repository EPR from agent "+agentID)
  }
}

@Path("/agent/{agentID}/index-epr")
class AgentGetIndexEPRService {
  @GET
  @Produces(Array("text/xml"))
  def getIndexEpr(@PathParam("agentID") agentID:String) = {
    val manager = actorsFor(classOf[Manager]).headOption.get
    (manager !! TakeAction(agentID, GetIndexEpr)).getOrElse("Couldn't get index EPR from agent "+agentID)
  }
}

@Path("/agent/{agentID}/credentials")
class AgentGetCredentialsService {
  @GET
  @Produces(Array("text/xml"))
  def getCredentials(@PathParam("agentID") agentID:String) = {
    val manager = actorsFor(classOf[Manager]).headOption.get
    (manager !! TakeAction(agentID, GetCredentials)).getOrElse("Couldn't get credentials from agent "+agentID)
  }
}

@Path("/agent/{agentID}/collection/list")
class AgentListCollections {
 private val logger = LoggerFactory.getLogger(getClass)
 @GET
 @Produces(Array("text/xml"))
 def listCollections(@PathParam("agentID") agentID:String) = {
  val manager = actorsFor(classOf[Manager]).headOption.get
  (manager !! TakeAction(agentID, ListCollections)).getOrElse("Couldn't get a list of collections from agent "+agentID)
 }
}



@Path("/agent/{agentID}")
class AgentPut {
  private val logger = LoggerFactory.getLogger(getClass)
  @PUT
  @Consumes(Array("text/xml"))
  @Produces(Array("text/xml"))
  def putAgent(@PathParam("agentID") agentID:String,
      //input:InputStream
      //inputJax:JAXBElement[String] // works but it isn't friendly
      input:String
      ) = {
    

    logger.debug("Welcome! This is our PUT's input: "+input)

    def checkAgentPutMethodInputAndReturnCerts(xmlString:String) : Option[(String,String)] = {
      val xml = XML.loadString(xmlString)
    // check that the XML is formed as we expect:
    
    //<credentials>
    //       <x509certificate>
    //           <![CDATA[{x509}]]>
    //       </x509certificate>
    //       <privatekey><![CDATA[{privKey}]]></privatekey>
    //</credentials>
    
    // xml should have:
    
    logger.debug("the credentials in XML format: "+xmlString)
        
    val iter = xml.iterator
    val topNode = try {
    	iter.next( )
    } catch {
      case e => {
    	  
    	  logger.error("malformed XML input for agent PUT method (no top-level node): "+xml.toString)   
    	  return None
      }
    }
      
    if (topNode.label != "credentials") { 
      logger.error("root element of XML input for agent PUT should be labeled 'credentials' in : "+xml.toString)
      return None
    }
    
    if (topNode.child.length != 2) {
      logger.error("credentials element should have two children, got:"+xml.toString)
      return None
    }
    
    val privateKeyNodeList = topNode.child.filter(c => c.label == "privatekey")
    val x509CertNodeList = topNode.child.filter(c => c.label == "x509certificate")
    if (privateKeyNodeList.length != 1 || x509CertNodeList.length != 1) {
      logger.error("couldn't find both privatekey element and x509certificate element in: "+xml.toString)
      return None
    }
    
    logger.debug("we've established that the right nodes exist in the agent's PUT method request entity XML")
    logger.debug("we need to examine the String input itself to get the appropriate things out of the CDATA fields")
    
    // this should equal '3':
    val splitOnCData = xmlString.split("<!\\[CDATA\\[")
    if (splitOnCData.length != 3) {
      logger.error("couldn't find <![CDATA[ tokens in XML input: "+xmlString)
      return None
    }
    
    val oneGoodString = try { splitOnCData.tail.head } catch { case e => e.printStackTrace;return None }
    val anotherGoodString = try { splitOnCData.tail(1) } catch { case e => e.printStackTrace;return None }
    
    val oneCert = try { oneGoodString.split("\\]\\]>").head } catch { case e=> e.printStackTrace;return None }
    val twoCert = try { anotherGoodString.split("\\]\\]>").head } catch { case e=> e.printStackTrace;return None }
 
    val testPub = "Sun RSA public key"
    if (!(oneCert.contains(testPub) || twoCert.contains(testPub))) {
      logger.error("couldn't find public key in "+xmlString)
      return None
    }
    
    val testPriv = "Sun RSA private CRT key"
    if (!(oneCert.contains(testPriv) || twoCert.contains(testPriv))) {
      logger.error("couldn't find private key in "+xmlString)
      return None
    }
      
    val resultX509 = if (oneCert.contains(testPub)) { oneCert } else { twoCert }
    val resultPrivKey = if (oneCert.contains(testPriv)) { oneCert } else { twoCert }
       
    
    Some(resultX509,resultPrivKey)
    }
    
    
    val x509AndPrivKeyTuple = checkAgentPutMethodInputAndReturnCerts(input) match {
      case Some((x509:String,privKey:String)) => {
        logger.info("agent put method input doc is sound, proceeding.") // proceed
        (x509,privKey)
      }
      case _ => throw new RuntimeException("malformed XML input for agent PUT method: "+input)
    }
 
    //logger.debug("agent credentials of  scala.xml.Text.toString: "+agentCredentialsXml.toString)
    
    
    // get the x509 certificate and the private key out of the XML
    // you won't be able to use scala match since there's CData inside
    
    def getContentsOfCDataElement(n:Node) = {
    	n.toString.substring(9,n.toString.length-3)
    }
//   RESTORE/FIX THE BELOW TWO LINES BEFORE PROCEEDING      
//      val x509 = getContentsOfCDataElement(x509NodeBox.head)
//      val privKey = getContentsOfCDataElement(privateKeyNodeBox.head)
    val manager = actorsFor(classOf[Manager]).headOption.get
    val x509 = x509AndPrivKeyTuple._1
    val privKey = x509AndPrivKeyTuple._2
    (manager !! VendAgent(agentID,x509,privKey)).getOrElse("Couldn't create an agent")            
      
  }
    
    //    agentCredentialsXml match {
//      case <credentials>
//             <x509certificate>{x509}</x509certificate>
//             <privatekey>{privKey}</privatekey>
//           </credentials> => { 
//             val manager = actorsFor(classOf[Manager]).headOption.get
//            (manager !! VendAgent(agentID,x509.toString,privKey.toString)).getOrElse("Couldn't create an agent")            
//           }
//      case _ => throw new RuntimeException("bad agent credentials format - check CDATA sanity")
   // } 
  
  
  
    
}

// deprecated -- use PUT with the URIName specified in CILogon x509 cert
// see http://wikis.sun.com/display/Jersey/Overview+of+JAX-RS+1.0+Features
//@Path("/manager/vend/{id}/{x509}/{privKey}")
//class ManagerVendService {
//  @GET // should probably be a PUT
//  @Produces(Array("text/xml"))
//  def vend(@PathParam("id") id:String,@PathParam("x509") x509:String,
//      @PathParam("privKey") privKey:String) = {    
//    val manager = actorsFor(classOf[Manager]).headOption.get
//    (manager !! VendAgent(id,x509,privKey)).getOrElse("Couldn't get test data")
//  }
//}
//
//@Path("/manager/list")
@Path("/agent")
class ManagerListService {
  @GET
  @Produces(Array("text/xml"))
  def list = {
    val manager = actorsFor(classOf[Manager]).headOption.get
    (manager !! ListAgents).getOrElse("Couldn't get test data")
  }
}

@Path("/manager/vend-bogus-agent")
class VendBogusUser {
  @GET
  @Produces(Array("text/xml"))
  def vend = {
	val manager = actorsFor(classOf[Manager]).headOption.get
	val x509Bogus = "bogus x509"
	val privKeyBogus = "bogus private key"
	val agentIDBogus = "urn:publicid:IDN+bogusID.org+user+A1Winner"
	(manager !! VendAgent(agentIDBogus,x509Bogus,privKeyBogus)).getOrElse("Couldn't vend a bogus agent")
  }
}






object XmlUtil {
  import xml._
  def addNode(n:Node,c:Node):Node = n match { case e:Elem => e.copy(child=e.child++c) }
}


//// see http://stackoverflow.com/questions/3754599/scala-create-structure-xml-from-lists-of-lists
//import xml._
//import transform._
//class AddPath(l: List[String]) extends RewriteRule {
//  // some inspiration on setting the node name here:
//  // val root_node_name = "root"
//  // val doc = new scala.xml.Elem(null, root_node_name, scala.xml.Null , scala.xml.TopScope)
//  //                                                    no Attributes                                                      
//  // result => doc: scala.xml.Elem = <root></root>
//  def listToNodes(l: List[String]): Seq[Node] = l match {
//    case Nil => Seq.empty
//    case first :: rest => 
//      <node>{listToNodes(rest)}</node> % Attribute("name", Text(first), Null)
//
//  }
//
//  def transformChild(child: Seq[Node]) = l match {
//    case Nil => child
//    case first :: rest =>
//      child flatMap {
//        case elem: Elem if elem.attribute("name") exists (_ contains Text(first)) =>
//          new AddPath(rest) transform elem
//        case other => Seq(other)
//      }
//  }
//
//  def appendToOrTransformChild(child: Seq[Node]) = {
//    val newChild = transformChild(child)
//    if (newChild == child)
//      child ++ listToNodes(l)
//    else
//      newChild
//  }
//
//  override
//  def transform(n: Node): Seq[Node] = n match {
//    case elem: Elem => elem.copy(child = appendToOrTransformChild(elem.child))
//    case other => other
//  }
//}
//
//// see http://stackoverflow.com/questions/2199040/scala-xml-building-adding-children-to-existing-nodes
//class AddChildrenTo(label: String, newChild: Node) extends RewriteRule {
//  def addChild(n: Node, newChild: Node) = n match {
//	case Elem(prefix, label, attribs, scope, child @ _*) =>
//	Elem(prefix, label, attribs, scope, child ++ newChild : _*)
//	case _ => error("Can only add children to elements!")
//  }
//  override def transform(n: Node) = n match {
//    case n @ Elem(_, `label`, _, _, _*) => addChild(n, newChild)
//    case other => other
//  }
//}