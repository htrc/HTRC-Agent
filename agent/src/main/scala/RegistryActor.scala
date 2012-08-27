
// the actor representing a registry

package htrcagent

import akka.actor.Actor
import akka.actor.Actor._
import scala.collection.mutable.HashMap
import akka.actor.Props
import scala.xml._

class RegistryActor extends Actor with Wso2Registry {

  import HtrcProps.RegistryProps._

  def receive = {

    case RegistryDownloadCollection(collectionName, username) =>
      sender ! downloadCollection(collectionName, username)

    case RegistryUploadCollection(data, username) =>
      sender ! uploadCollection(data, username)

    case RegistryModifyCollection(data, username) =>
      sender ! modifyCollection(data, username)

    // returns full properties file for an algorithm
    case RegistryAlgorithmDetails(username, algorithmName) => 
      sender ! getAlgorithmInfo(username, algorithmName)

    // simple algorithm list
    case RegistryListAvailibleAlgorithms(username) =>
      sender ! getAlgorithmsXml(username)
      
    case RegistryListAvailibleCollections(username) =>
      sender ! getCollectionsXml(username)

    case RegistryAlgorithmProperties(algorithmName, username) =>
      sender ! getAlgorithmInfo(username, algorithmName)

    case FetchRegistryData(data, workingDir) =>
      sender ! writeData(data, workingDir)

    case FetchRegistryCollections(names, workingDir, username) =>
      sender ! writeCollections(names, workingDir, username)

  }

  def downloadCollection(collectionName: String, username: String): Elem = {
    // find path
    val paths = getCollectionPaths(username)
    val collectionOpt = paths find { _.split('/').last == collectionName }
    collectionOpt match {
      case Some(path: String) =>
       <collection>
         {getCollectionProperties(path)}
         {getCollectionData(path)}
       </collection>
      case _ => <error>invalid collection name (possibly corrupt?) : {collectionName}</error>
    }

  }

  def uploadCollection(data: NodeSeq, username: String): Elem = {
    // convert properties to hashmap
    val properties = new HashMap[String,String]
    ((data \ "collection_properties").head \ "e") map { elem =>
      properties += ((elem \ "@key" text) -> elem.text)
    }
    // insert missing data
    properties += ("owner" -> username)

    // compute path
    val path = 
      if(properties("availability") == "public")
        collectionsPath+"/public/"+properties("name")
      else
        collectionsPath+"/private/"+username+"/"+properties("name")

    // check to see if the collection already exists
    val exists = regOp { registry.resourceExists(path) }

    if(exists)
      <error>
        <message>this collection already exists, use modify instead to change</message>
        {data}
      </error>
    else {

      // convert to a newline delimited file       
      val parsedData = (data \\ "id") map { _.text.trim } mkString("\n")
      
      // perform the upload
      putCollectionFolder(path, parsedData.toString, properties)
      <collection>{properties("name")}</collection>

    }
      
  }

  def modifyCollection(data: NodeSeq, username: String): Elem = {
    // convert input xml to hashmap                                                     
    val properties = new HashMap[String,String]
    ((data \ "collection_properties").head \ "e") map { elem =>
      properties += ((elem \ "@key" text) -> elem.text)
    }
    // insert missing data
    properties += ("owner" -> username)
    
    // todo : load strings from config file
    val path =
      if(properties("availability") == "public")
        collectionsPath+"/public/" + properties("name")
      else
        collectionsPath+"/private/"+username+"/"+properties("name")
    
    val parsedData = (data \\ "id") map { _.text.trim } mkString("\n")
  
    // check if the original has the same owner, if so allow, else fail
    val original = getAlgorithmInfo(path)
    val ownerNodeOpt = (original \ "e") find { elem => (elem \ "@key" text) == "owner" }
    val ownerNode = ownerNodeOpt.getOrElse(<e key="owner">NO_OWNER</e>)
    val sameOwner = (ownerNode text) == username

    if(sameOwner) {
      putCollectionFolder(path, parsedData.toString, properties)
      <collection>{properties("name")}</collection>
    } else {
      <error>collection exists and is owned by a different user</error>
    }
  }

}
