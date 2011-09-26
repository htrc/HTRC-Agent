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
class CachedEPR(name: String, refreshEpr: (() => URI), expirationTime: Int = 3600000 /*milliseconds*/ ) {
  
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