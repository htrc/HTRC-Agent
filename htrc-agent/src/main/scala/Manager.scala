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

class Manager extends Actor {
  
  private val logger = LoggerFactory.getLogger(getClass)
  private val agentMap = new HashMap[String,ActorRef]
  
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
      //logger.debug("VendinProtobufg agent for id: %s",id)
      if (validCredentials(uriName,x509,privKey)) // always succeeds :\
         { 
           getAgent(uriName) match {
             case None => {  
               val newAgent = actorOf(new Agent(uriName,x509,privKey)).start()
               val newAgentID = (newAgent ? GetAgentIDAsString).get //OrElse("Failed")
               if (newAgentID == "Failed") throw new RuntimeException            			 
               		logger.debug("===> putting a new agent to the agent map")
               agentMap.put(newAgentID.toString(),newAgent)
               self reply <agentID>{newAgentID}</agentID>
             }
             case Some(agentActorRef) => {
               logger.debug("===> an agent already existed for this uriName: "+uriName)
               self reply <agentID>{uriName}</agentID>
             }
           }
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
      
    case _ => self reply <error>This is not a valid command</error>
  }
}