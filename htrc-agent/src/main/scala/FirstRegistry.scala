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
import org.apache.commons.codec.binary.Hex
import java.io.FileReader

class FirstRegistry(
    
  copyAlgoJarToWorkingDir: Boolean,
  timeStarted: Date,
  logger: Logger,
  registryClientInitializer: (()=>RegistryClient),
  runtimeProps: Properties
  
) extends RegistryHelper {
  
  // To make things easy I'm including all the startup props an agentSlave has here.
  // This makes registry a class not an object (questionable) and ties agent state to the registry.
  // The state-association may be useful but concerns me.
  
  var registryClient = registryClientInitializer()
  
  def reinitializeRegistryClient = {
    registryClient = registryClientInitializer()
  }
  
  def getAlgorithmExecutable(algoName: String, workingDir: String): Option[String] = {
    
	    def getAlgoFromRegistryHelper = {
	    // this method should contact the registry and return a file(executable)
	      
		    val jarFile:String = try {
		      registryClient.getScriptFile(algoName)
		     
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
  
  
  
  	def getCollectionVolumeIDs(collectionName:String) = {
  	  
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
  
  def listCollections = {
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
  
 def listAvailableAlgorithms = {
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

 
  def encodeUserURNForRegistry(userURN:String) : String = {
    // for now we use a Hex encoding to do this.  Registry API is extremely choosy about
    // what characters we use ... = % + etc are all off limits
    
    // it's an array of characters... you need to use the following "new String"
    // way of doing things instead of xyz.toString
    new String(Hex.encodeHex(userURN.getBytes()))
  }
  
  def decodeUserURNFromRegistry(userIDInRegistry:String) : String = {
    logger.warn("!!!!> Need to test decodeUserURNFromRegistry")
    Hex.decodeHex(userIDInRegistry.toCharArray()).toString
  }
  
  private def registryResultPathPrefix = "/results"
  
  def postResultsToRegistry(userURN:String, 
      algorithmID:String,
      theResultList:List[AlgorithmResult]) = {
      // need to convert the user URN to an acceptable hex string
      // so that registry doesn't choke
      val userNameAsHex = encodeUserURNForRegistry(userURN)
      println("===> Results to post: "+theResultList.toString)
      val registrySuccessPathList = for (result <- theResultList) yield ({ 
        
        logger.warn("!!!!> We stream in huge files when posting them to the registry")
        logger.warn("!!!!> This needs to be corrected..")
        // Yes, we will stream the entire thing in and post it.  This 
        // is a horrible idea, and needs to be corrected.
        val (resultID,resultAsString) = result match {
          case StdoutResult(outstring) => {
            ("stdout",outstring)
          }
          case StderrResult(outstring) => {
            ("stderr",outstring)
          }
          case FileResult(workingDir,fileName) => {
            val buf = (new BufferedReader(new FileReader(workingDir+File.separator+fileName)))
            val strBuf = new StringBuffer
            while (buf.ready) {
              strBuf.append(buf.readLine())
            }
            buf.close()
            ("file/"+fileName,strBuf.toString)
            
          }
        }
        val resourcePath = this.registryResultPathPrefix + "/"+userNameAsHex+"/"+algorithmID+"/"+resultID
        logger.info("====> Posting a result for user "+userURN+" to this registry resource path : "+resourcePath)
        val metadataToPost = "<result><userURN>"+userURN+"</userURN></result>"
        val resultPath= { 
            def tryIt() = { 
              try {
                registryClient.postResourse(   
                		
                		resourcePath,
                		resultAsString,
                		metadataToPost
                		) }
              catch {
                case e => { logger.error(e.getStackTrace().toString) ; null }
              }
            }
            
            // need to retry this a few times, potentially
            var count = 0
            var temp:String = null
            while (temp == null && count < 10) {
              temp = tryIt()
              count = count + 1
            }
            if (temp == null) {
              throw new RuntimeException("couldn't post resource to registry: "+(resourcePath,resultAsString,metadataToPost))
            } else {
              temp
            }
        }
        
        // used this before i had retries
//        val resultPath = registryClient.postResourse(   
//                		
//                		resourcePath,
//                		resultAsString,
//                		metadataToPost
//                		) 
//        println("====> registry posting value is :"+resultPath)
//        
        
        resultPath
      }) 
      
	  // for now we return none , until we can get a return val out of the above postResource call
	  registrySuccessPathList
	  // above could have null vals!
	  
	    
  }
}