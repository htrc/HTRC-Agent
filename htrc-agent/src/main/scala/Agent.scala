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
import java.io.FileReader
import akka.dispatch.Future
  
  
class Agent(userID: String,x509: String,privKey: String) extends Actor with Loggable {
  
  // since akka provides an actor registry we can use that for look ups and eliminate our own agentID book keeping
  // we use URIName of the x509 cert for the id
  self.id = userID
  
  // the new registry actor
  val registryOption = actorFor[RegistryActor]
  val ourRegistry = 
    if(registryOption == None) {
    	val preInit = actorOf[RegistryActor]
    	preInit.start()
    	preInit
    }
    else
    	registryOption.get
    	
  private val copyAlgoJarToWorkingDir = true
  private val launchScript:String = RuntimeProperties("algolaunchscript").toString
  val agentRef = self
  
  // algorithm status is here, we don't have compute actors yet
  val algorithmRunStatusMap = new HashMap[String,AlgorithmRunStatus]
                          //e.g.   huetoanhu-4731sssa-fueoauht, 'Initializing                                  
		     			  // 	   huetoanhu-4731sssa-fueoauht, 'Running
                          //       huetoanhu-4731sssa-fueoauht, 'Finished
  
  // helpers for basic tasks
  
  private def generateAlgorithmRunID: String = UUID.randomUUID().toString
  
  private def updateAlgorithmRunStatus(algoID: String, status: AlgorithmRunStatus) = {
    logger.debug("algorithmRunStatusMap before put of "+(algoID,status)+": "+algorithmRunStatusMap)
    algorithmRunStatusMap.put(algoID,status)
    logger.debug("algorithmRunStatusMap after put of "+(algoID,status)+": "+algorithmRunStatusMap)
  }
  
  private def getAlgorithmRunStatus(algoID:String): Option[AlgorithmRunStatus] = algorithmRunStatusMap.get(algoID)
   
  private def credentialsToXml = {
    <credentials>
        <x509certificate>{x509}</x509certificate>
        <privatekey>{privKey}</privatekey>
    </credentials>
  }
  private def toXml: scala.xml.Elem = {
    <agent>
      <agentID>{self.id}</agentID>
	  <userID>{self.id}</userID>
      {credentialsToXml}
	</agent>
  }
  
  private def algorithmRunExists(algoID:String) = {
    algorithmRunStatusMap.contains(algoID)
  }
  
  private def listAllAlgorithmRunIDs = {
    logger.info("===> what keys are inside the algorithm run status map ? "+algorithmRunStatusMap.keys)
    for (k <- algorithmRunStatusMap.keys) yield k
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

  
  private def formAlgorithmRunXMLResponse(algoID: String) = {
    logger.info("====> in formAlgorithmRunXMLResponse")
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
       val hrefPrefix = "/agent/"+self.id+"/algorithm/"+algoID+"/result/"
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
        throw new RuntimeException("no such algorithm "+algoID+ " for agent "+self.id)
      }
    
  }
  
  private def formAlgorithmRunListXMLResponse() = {
    //logger.debug("===> listAllAlgorithmRunIDs: "+listAllAlgorithmRunIDs)
    <algorithmRunList>
	  {for (a <- listAllAlgorithmRunIDs) yield formAlgorithmRunXMLResponse(a)}
    </algorithmRunList>
  }
  
  // end 2011-09-22 changes
  
  def algoResultMessage(algoResultReq: AlgorithmResultRequest) = {
  
        logger.debug("===> trying to get algorithm result")
      val myAlgoID = algoResultReq match {
        case StdoutResultRequest(algoID) => {        
          algoID
        }
        case StderrResultRequest(algoID) => {
          algoID
        }
        case FileResultRequest(algoID:String,filename:String) => {         
          algoID
        }
        case _ => {
          throw new RuntimeException ("unspecified case in gathering result - 0x1")
        }
      }
      logger.debug("====> we matched the algoID: "+myAlgoID)
      val myStatusMap = getAlgorithmRunStatus(myAlgoID)
      // if the algorithm doesn't have finished or crashed status,
      // this request doesn't make any sense -- return an error
     

      val myResultSet = myStatusMap match {
        case Some(Finished(time: Date, 
        		           workingDir: String, 
        		           algorithmResults:AlgorithmResultSet)) => {
           Some(algorithmResults)
          
        }
        case Some(Crashed(time: Date, 
        		          workingDir: String, 
        		          algorithmResults:AlgorithmResultSet)) => {
           Some(algorithmResults)
        }
        case _ => {
          // algorithm ID doesn't exist, or it's not finished or crashed
          logger.warn("couldn't find algo ID")
          None
        }
      }
      
      logger.debug("====> we matched the results: "+myResultSet)
      if (myResultSet == None) {
        logger.warn("Result set was none.")
        self reply <error>Couldn't obtain algorithm results for {myAlgoID}.</error> 
      } else {
        
      logger.warn("===> find out what kind of request this is ")
      // return the correct result  -- a specific file, or stdout or stderr
      val requestedResult = algoResultReq match {
        case StdoutResultRequest(algoID) => {            
          myResultSet.get.l.find((res=>res match {
            case StdoutResult(s)=>true
            case _=>false}))
        }
        case StderrResultRequest(algoID) => {
          myResultSet.get.l.find((res=>res match {
            case StderrResult(s)=>true
            case _=> false}))         
        }
        case FileResultRequest(algoID:String,filename:String) => {        
          myResultSet.get.l.find((res=>res match {
            case FileResult(wd,fn)=>{
            	fn == filename
            }
            case _=>false}))
           
         
          // need to get the working directory of the run
          // then check whether the file exists
           
          // if it doesn't exist, check to see if it was put into the registry
        }
        case _ => {
          throw new RuntimeException("unspecified case in gathering result - 0x0")
        }
      }
      
      if (requestedResult == None) {
        self reply <error>Couldn't find requested result for algorithm {myAlgoID}.</error>
      } else {
      
      val myResult = requestedResult.getOrElse(throw new RuntimeException("result didn't exist"))
      
      
      // build the  the response
      logger.warn("building response")
      AgentUtils.renderResultOutput(myAlgoID,myResult)
      }
      }
    }
  
  def updateAlgorithmRunStatusMessage(algoID: String, status: AlgorithmRunStatus) = {
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
  
  def runAlgorithmMessage(algorithmName: String, collectionName: String, userArguments: List[String]) = {
      logger.debug("====> We're in RunAlgorithm case of Agent's receive block")
      // we then generate an algorithRunID which will be used to check up on this algorithm run
      val algoID = generateAlgorithmRunID
      logger.debug("    ====> Generated algorithm run ID: "+algoID)
      // parallelize this when you get a chance
      //val volumeIDs = slaveRouter !! GetCollectionVolumeIDs(collectionName)
      
      // update the run status to prestart -- nothing has happened yet, but we have an ID for the algorithm
      updateAlgorithmRunStatus(algoID,Prestart(new Date))
      logger.debug("    ====> Updated algorithm run status to Prestart")

      logger.debug("    ====> Telling a slave to start the algorithm "+algoID+" '%s'".format(algorithmName))
      
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
      
      val algo = new ExecutableAlgorithm(algoID, algorithmName, userArguments, collectionName, 
          initialDir, workingDir, agentRef, userID)
	  
      spawn {
        algo.instantiate()
      }
        
      formAlgorithmRunXMLResponse(algoID)
           
    }
  
    // spawns a child actor tasked with asynchronously handling and responding to the message
  def asyncReply[T](f: =>T) = {
    val messageDestination = self.channel
    spawn {
      messageDestination ! f
    }
  }
  
  // AGENT RECEIVE BLOCK
  // AGENT RECEIVE BLOCK
  // AGENT RECEIVE BLOCK
  // AGENT REVEICE BLOCK
  
  def receive = {
    
    case GetAlgorithmRunResult(algoResultReq: AlgorithmResultRequest) => {
      	asyncReply { algoResultMessage(algoResultReq) }
    }
      	
    // these need to be collapsed
    case GetUserIDAsString => self.reply(self.id)
    case GetAgentIDAsString => self.reply(self.id)
    
    case GetAgentID => self.reply(<agentID>{self.id}</agentID>)

    case ListAvailableAlgorithms => {
      logger.debug("INSIDE **AGENT** ListAvailableAlgorithms")
      asyncReply { (ourRegistry ? RegistryListAvailableAlgorithms).as[xml.Elem].get }
    }
    
    case ListCollections => {
      logger.debug("INSIDE **AGENT** ListAvailableAlgorithms")
      asyncReply { (ourRegistry ? RegistryListCollections).as[xml.Elem].get }
    }
    
    case GetCredentials=> self reply credentialsToXml
    
    case UpdateAlgorithmRunStatus(algoID: String, status: AlgorithmRunStatus) => {
      updateAlgorithmRunStatusMessage(algoID, status)
    }
    
    case PollAlgorithmRunStatus(algoID: String) => {
       self reply formAlgorithmRunXMLResponse(algoID)
    }
    
    case ListCurrentAlgorithms => {
      self reply formAlgorithmRunListXMLResponse()
    }
    
    case RunAlgorithm(algorithmName: String, collectionName: String, userArguments: List[String]) => {
      asyncReply { runAlgorithmMessage(algorithmName, collectionName, userArguments) }   
    }
    
    case _ => self.reply(<error>Invalid action</error>)
    
  }
}