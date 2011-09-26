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

class AgentSlave(agentRef: ActorRef, registryClient: RegistryClient, 
    userID: String, x509: String, privKey: String,runtimeProps: Properties) extends Actor {
 
  private val changeUserDirOnAlgoFetch = false
  private val copyAlgoJarToWorkingDir = true
  private val launchScript:String = runtimeProps.get("algolaunchscript").toString()
  private val logger = LoggerFactory.getLogger(getClass)
  
  private def getAlgorithmExecutable(algoName: String, workingDir: String): Option[String] = {
    
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
	      val EmergencyAlgorithmExecutableStorageDirectory = "emergency-algorithm-executable-storage"
	      
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
	       logger.debug("====> runtime property of 'skipgettingalgoexecutablefromregistry' is " + skipProp.toString)
		  
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
	    		FileUtils.copyFile(new File(optionFileNameString.head),	new File(workingDir + File.separator + filenameOnly))
	    	}
	    	Some(filenameOnly)
	    } else 
	    	None
  	}
  
  
  
  	private def getCollectionVolumeIDs(collectionName:String) = {
  	  
	  	logger.debug("!!!!> inside getCollectionVolumeIDs, asked for "+collectionName)
	  	val volumeIDs:java.util.List[String] = registryClient.getVolumeIDsFromCollection(collectionName)
	  	logger.debug("!!!!> got the list of volumes, size is"+volumeIDs.toList.length)
	  	volumeIDs.toList
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