
// these are messages used to communicate information between actors

// convention is messages that are only to be handled by a specific
// thing have that thing as the first part of the name

// the agent is excluded from this for now

// todo : come up with some sensible hierarchy of traits not just HtrcMessage

package htrcagent

import scala.xml._
import scala.collection.mutable.HashMap

trait HtrcMessage

// the four calls involving the registry

case class UploadCollection(data: NodeSeq) extends HtrcMessage
case class RegistryUploadCollection(data: NodeSeq, username: String) extends HtrcMessage

case class ModifyCollection(data: NodeSeq) extends HtrcMessage
case class RegistryModifyCollection(data: NodeSeq, username: String) extends HtrcMessage

case class DownloadCollection(collectionName: String) extends HtrcMessage
case class RegistryDownloadCollection(collectionName: String, username: String) extends HtrcMessage

case object ListAvailibleCollections extends HtrcMessage
case class RegistryListAvailibleCollections(username: String) extends HtrcMessage

case object ListAvailibleAlgorithms extends HtrcMessage
case class RegistryListAvailibleAlgorithms(username: String) extends HtrcMessage

case class AlgorithmDetails(algorithmName: String) extends HtrcMessage
case class RegistryAlgorithmDetails(username: String, algorithmName: String) extends HtrcMessage

// the process of running an algorithm

case class RunAlgorithm(algorithmName: String, body: NodeSeq) extends HtrcMessage

case class FetchRegistryData(data: HashMap[String,String], workingDir: String)

case class RegistryAlgorithmProperties(algorithmName: String, username: String) extends HtrcMessage

case class AlgorithmStatusRequest(algId: String) extends HtrcMessage

case class WorkerUpdate(status: AlgorithmStatus) extends HtrcMessage

case class ResultUpdate(result: AlgorithmResult) extends HtrcMessage

