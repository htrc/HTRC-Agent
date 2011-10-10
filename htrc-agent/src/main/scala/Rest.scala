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
import akka.dispatch.Future

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

// just added these three endpoints 2011-10-03
@Path("/agent/{agentID}/algorithm/{algoID}/result/console/stdout")
class AgentGetAlgorithmStdoutConsoleResultService {
  // this method retrieves the stdout console result of an algorithm
  
  // in the medium-term, it should stream the result across.
  // in the short term we may just return the entire thing at once
  private val logger = LoggerFactory.getLogger(getClass)
  @GET
  @Produces(Array("text/xml"))
  def getAlgoResult(@PathParam("agentID") agentID:String,
                  @PathParam("algoID") algoID: String) = {
    logger.debug("====> handle REST method for stdout result console")
    val manager = actorsFor(classOf[Manager]).headOption.get
    (manager !! TakeAction(agentID,
        GetAlgorithmRunResult(StdoutResultRequest(algoID)))).getOrElse(
            "Couldn't get algorithm run stdout  for agent "+agentID+ " with algorithm ID "+algoID)
  
  }
}

@Path("/agent/{agentID}/algorithm/{algoID}/result/console/stderr")
class AgentGetAlgorithmStderrConsoleResultService {
  // this method retrieves the stderr console result of an algorithm
  
  // in the medium-term, it should stream the result across.
  // in the short term we may just return the entire thing at once
  private val logger = LoggerFactory.getLogger(getClass)
  @GET
  @Produces(Array("text/xml"))
  def getAlgoResult(@PathParam("agentID") agentID:String,
                  @PathParam("algoID") algoID: String) = {
    val manager = actorsFor(classOf[Manager]).headOption.get
    (manager !! TakeAction(agentID,
        GetAlgorithmRunResult(StderrResultRequest(algoID)))).getOrElse(
            "Couldn't get algorithm run result stderr for agent "+agentID+ " with algorithm ID "+algoID)
  
  }
}

@Path("/agent/{agentID}/algorithm/{algoID}/result/file/{filename}")
class AgentGetAlgorithmFileResultService {
  // this method retrieves a file output result of an algorithm
  // client specifies the filename.  will need to check if it exists
  
  // in the medium-term, it should stream the result across.
  // in the short term we may just return the entire thing at once
  private val logger = LoggerFactory.getLogger(getClass)
  @GET
  @Produces(Array("text/xml"))
  def getAlgoResult(@PathParam("agentID") agentID:String,
                  @PathParam("algoID") algoID: String,
                  @PathParam("filename") filename: String) = {
    val manager = actorsFor(classOf[Manager]).headOption.get
    (manager !! TakeAction(agentID,
        GetAlgorithmRunResult(FileResultRequest(algoID,filename)))).getOrElse(
            "Couldn't get algorithm run result file for agent "+agentID+ " with algorithm ID "+algoID+" and filename "+filename)
  }
}

// end new addition 2011-10-03

@Path("/agent/{agentID}/algorithm/poll/{algoID}")
class AgentPollAlgorithmRunStatusService {
  private val logger = LoggerFactory.getLogger(getClass)
  @GET
  @Produces(Array("text/xml"))
  def getAlgoStatus(@PathParam("agentID") agentID:String,
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
  def runAlgo(@PathParam("agentID") agentID:String,
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
    (manager !!! TakeAction(agentID, ListAvailableAlgorithms)).get //OrElse("couldn't find available algorithms")
  }
}

@Path("/agent/{agentID}/repository-epr")
class AgentGetRepositoryEPRService {
  @GET
  @Produces(Array("text/xml"))
  def getRepositoryEpr(@PathParam("agentID") agentID:String) = {
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
  val res = (manager !!! TakeAction(agentID, ListCollections)).get
  /*if(res == None) {
    <error>"Couldn't get a list of collections from agent "+{agentID}</error>
  } else {
    val res2 : xml.Elem = res.get.asInstanceOf[xml.Elem]
    res2
  }
  */
  //val res2:String = res.getOrElse("Couldn't get a list of collections from agent "+agentID)
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