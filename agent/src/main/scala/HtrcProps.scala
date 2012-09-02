
// an object to keep track of props management

package htrcagent

import com.typesafe.config.ConfigFactory

object HtrcProps {

  private val config = ConfigFactory.load("htrc.conf")
 
  object RegistryProps {
    val uri = config.getString("htrc.registry.uri")
    val collectionsPath = config.getString("htrc.registry.paths.collections")
    val algorithmsPath = config.getString("htrc.registry.paths.algorithms")
    val username = config.getString("htrc.registry.auth.username")
    val password = config.getString("htrc.registry.auth.password")
  }

  val solr = config.getString("htrc.urls.solr")
  val dataApi = config.getString("htrc.urls.data_api")

  val resultStoragePath = config.getString("htrc.directory.storage")
  val workingDirRoot = config.getString("htrc.directory.working")
  val resultRootUrl = config.getString("htrc.urls.results")
  val odinLocation = config.getString("htrc.urls.odin")

  val jobSaveLocation = config.getString("htrc.registry.paths.jobs")
  
}
