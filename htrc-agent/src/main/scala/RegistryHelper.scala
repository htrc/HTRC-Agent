package htrcagent

trait RegistryHelper {
  
  def getAlgorithmExecutable(algoName: String, workingDir: String): Option[String]
  
  def writeVolumesTextFile (volumes:List[String],workingDir: String):String
  
  def listCollections: scala.xml.Elem
  
  def listAvailableAlgorithms: scala.xml.Elem
  
  def getCollectionVolumeIDs(collectionName:String): List[String]

}