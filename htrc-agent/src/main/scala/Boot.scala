/** 
 * Copyright Data to Insight Center of Indiana University.
 * Extended from code provided by Matt Bowen.
 * Distributed under the Apache 2 license
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */

package htrcagent

import akka.actor.SupervisorFactory
import akka.config.Supervision._
import akka.actor.Actor._
import java.util.Properties
import java.io.File
import org.slf4j.{Logger,LoggerFactory}


class Boot  {
  
 // debug("Logger started")
 private val logger = LoggerFactory.getLogger(getClass)
 
 // Note that src/resources/log4j.properties was key to getting the logger together
 // Also note that I had to find an old 1.5.5 slf4j jar to get this to work, the
 // 1.6+ jar doesn't like akka?

  
  // Set some properties used by Axis2 config
  // This will be used by RegistryClient
  // hardcoded: the keystore file is in config/wso2carbon.jks
  val keyStore = "config" + File.separator + "wso2carbon.jks" 
  val keyStoreFile = (new File(keyStore))
  logger.warn("wso2carbon.jks keystore file exists and is readable? " +
      (keyStoreFile.exists() && keyStoreFile.canRead()))
  logger.warn("Absolute file path to keystore: "+keyStoreFile.getAbsolutePath)
  

  System.setProperty("javax.net.ssl.trustStore", keyStore)
  System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon")
  System.setProperty("javax.net.ssl.trustStoreType", "JKS")
  
  val factory = SupervisorFactory(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      Supervise(
	actorOf[Manager], 
	Permanent) 
      :: Nil))
  
  factory.newInstance.start
  
  
}
