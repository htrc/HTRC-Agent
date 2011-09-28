package htrcagent

trait RegistryHelper {
  
  def getAlgorithmExecutable(algoName: String, workingDir: String): Option[String]
  
  def writeVolumesTextFile (volumes:List[String],workingDir: String):String
  
  def listCollections: scala.xml.Elem
  
  def listAvailableAlgorithms: scala.xml.Elem
  
  def getCollectionVolumeIDs(collectionName:String): List[String]

  // storing URNs for users doesn't work; 
  // the next two methods turn them into something the registry API can deal with
  def encodeUserURNForRegistry(userURN:String) : String 
	
  def decodeUserURNFromRegistry(encodedUserString:String) : String

}