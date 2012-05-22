
package htrcagent

import akka.actor.Actor
import akka.actor.Actor._
import scala.collection.mutable.HashMap

import akka.actor.Props

import akka.dispatch.Future
import akka.dispatch.Future._
import akka.dispatch.Await
import akka.pattern.ask
import akka.util.duration._
import akka.util.Timeout

import org.apache.axis2.context.ConfigurationContext
import org.apache.axis2.context.ConfigurationContextFactory
import org.wso2.carbon.registry.core.{ Registry, Comment, Resource }
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient

class RegistryActor extends Actor with Wso2Registry {

  implicit val timeout:Timeout = Timeout(5 seconds)

  import context._

  restartRegistry

  def receive = {

    case ListAvailibleAlgorithms =>
      restartRegistry
      testRegistry
      sender ! "factorial" :: Nil

    case ListAvailibleCollections =>
      sender ! "5" :: Nil

    case GetAlgorithmExecutable(algName, workingDir) =>
      // need to do something real
      sender ! "echo 120"

    case GetAlgorithmData(colName, workingDir) =>
      //need to do something real
      sender ! true

  }

  def testRegistry = {

    restartRegistry
    
    val resource = registry.newResource
    resource.setContent("Hello, htrc")
    registry.put("/testing", resource)
    println("Resource added to /testing!")
    
    val comment = new Comment()
    comment.setText("did I really have to call settext instead of pass an argument?")
    registry.addComment("/testing", comment)
    println("comment added to resource!")

    val got = registry.get("/testing")
    println("The resource is: " + resource.getContent)

  }

}

trait Wso2Registry {

  def initialize: WSRegistryServiceClient = {

    System.setProperty("javax.net.ssl.trustStore", "wso2-config/client-truststore.jks")
    System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon")
    System.setProperty("javax.net.ssl.trustStoreType", "JKS")
    
    val axis2repo = "wso2-config/client"
    val axis2conf = "wso2-config/axis2_client.xml"
    
    val configContext = ConfigurationContextFactory.
    createConfigurationContextFromFileSystem(axis2repo, axis2conf)
    
    val username = "admin"
    val password = "BillionsOfPages11"
    val serverUrl = "https://smoketree.cs.indiana.edu:9443/services/"

    new WSRegistryServiceClient(serverUrl, username, password, configContext)

  }

  var registry: WSRegistryServiceClient = null

  def restartRegistry = {
    registry = initialize
  }

}
