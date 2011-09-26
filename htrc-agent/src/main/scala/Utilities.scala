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