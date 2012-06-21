package htrcagent

trait HtrcMessage

case class AlgStdout(algId: String) extends HtrcMessage
case class AlgStderr(algId: String) extends HtrcMessage
case class AlgFile(algId: String, filename: String) extends HtrcMessage

case class PollAlg(algId: String) extends HtrcMessage

case object ListAgentAlgorithms extends HtrcMessage

case object ListAvailibleAlgorithms extends HtrcMessage
case object ListAvailibleCollections extends HtrcMessage
case class RunAlgorithm(algName: String, colName: String, args: String) extends HtrcMessage

case class HtrcCredentials(x509: String, privateKey: String)

case class GetAlgorithmData(colName: String, workingDir: String)
case class GetAlgorithmExecutable(algName: String, workingDir: String)
case class GetAlgorithmInfo(algName: String) 
case class WriteDependencies(alg: String, workingDir: String)

case class WorkerUpdate(status: AlgorithmStatus)

case class SolrQuery(params: Seq[(String,String)])
