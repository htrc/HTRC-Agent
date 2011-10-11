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


// ====================
// ===== Messages =====
// ====================

//
// the following define the things the Manager can do
//
sealed trait ManagerAction
case class VendAgent(userID: String, x509: String, privKey: String) extends ManagerAction // org.cilogon.portal.util._
// have the agent call its preStart method to get data from registry
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

case class GetAlgorithmRunResult(algoResultReq:AlgorithmResultRequest) extends AgentAction

case class GetCollectionVolumeIDs(collectionName: String) extends AgentAction

//utility messages... less interesting:
case object GetCredentials extends AgentAction
case object GetAgentID extends AgentAction
case object GetAgentIDAsString extends AgentAction
case object GetUserIDAsString extends AgentAction
case class UpdateAlgorithmRunStatus(algoID: String, status: AlgorithmRunStatus)

sealed trait AlgorithmResultRequest
case class StdoutResultRequest(algoID:String) extends AlgorithmResultRequest
case class StderrResultRequest(algoID:String) extends AlgorithmResultRequest
case class FileResultRequest(algoID:String, fileName:String) extends AlgorithmResultRequest


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