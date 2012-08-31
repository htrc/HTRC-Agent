
// a client for the htrc agent that uses this http client

// this "new" version is for the reworked agent api

package htrcagentclient

import httpbridge._

import akka.dispatch.Future

import scala.xml._
import play.api.libs.json._

class HtrcAgentClient(username: String = HtrcProps.debugUser, password: String = HtrcProps.debugPass) extends HttpClient {

  var token: String = "no_token"
  def auth = ("Authorization", "Bearer " + token) :: Nil
  val root = :/(HtrcProps.agentRoot)
  
  def checkErr(in: Future[Either[HttpError, HttpResponse[NodeSeq]]]): Future[Either[String,NodeSeq]] = {
    in map {
      case Left(err) => Left("decoder error: " + err.statusCode)
      case Right(res) if (res.statusCode == 200) => Right(res.body)
      case Right(res) => Left("error: " + res.statusCode)
    }
  }

  def warmCache =
    checkErr(get[NodeSeq](root / "admin" / "cache" / "collections" / "load"))

  def downloadCollection(name: String) = 
    checkErr(get[NodeSeq](root / "download" / "collection" / name, auth))

  def modifyCollection(data: NodeSeq) = 
    checkErr(put[NodeSeq,NodeSeq](root / "modify" / "collection", data, auth))

  def uploadCollection(data: NodeSeq) =
    checkErr(put[NodeSeq,NodeSeq](root / "upload" / "collection", data, auth))

  val testCollection =
    <collection>
      <collection_properties>
        <e key="name">An elaborate and clever collection name</e>
        <e key="description">Some descriptive text</e>
        <e key="availability">public</e>
        <e key="tags">argle,bargle,nonsense</e>
      </collection_properties>
      <ids>
        <id>book1</id>
        <id>book2</id>
        <id>book3</id>
        <id>book4</id>
      </ids>
    </collection>

  val testCollection120 =
    <collection>
      <collection_properties>
        <e key="name">testCollection120</e>
        <e key="description">Some descriptive text</e>
        <e key="availability">public</e>
        <e key="tags">argle,bargle,nonsense</e>
      </collection_properties>
      <ids>
        <id>book1</id>
        <id>book2</id>
        <id>book3</id>
        <id>book4</id>
      </ids>
    </collection>

  // cleaner collection upload
  def uploadCollection(name: String, 
                       description: String,
                       availability: String,
                       tags: List[String],
                       path: String): Future[Either[String,NodeSeq]] = {
    // load in the ids from the file
    val s = scala.io.Source.fromFile(path)
    // make the xml to upload
    val xml = 
      <collection>
        <collection_properties>
          <e key="name">{name}</e>
          <e key="description">{description}</e>
          <e key="availability">{availability}</e>
          <e key="tags">{tags.mkString(",")}</e>
        </collection_properties>
        <ids>
          {for (id <- s.getLines) yield <id>{id}</id>}
        </ids>
      </collection>

    uploadCollection(xml)

  }

  def uploadCollectionTest = 
    uploadCollection("Test_Collection1", "Test collection of 3 volumes with authors named Dickens and not Charles", "public", Nil, "/home/toddaaro/htrc/Test_Collection1")

  def listAlgorithms = checkErr(get[NodeSeq](root / "algorithm" / "list", auth))

  def algorithmDetails(algorithmName: String) =
    checkErr(get[NodeSeq](root / "algorithm" / "details" / algorithmName, auth))
  
  def listCollections = checkErr(get[NodeSeq](root / "collection" / "list", auth))

  def runAlgorithm(algorithmName: String, userProperties: NodeSeq) = {
    val path = root / "algorithm" / "run" / algorithmName
    checkErr(put[NodeSeq,NodeSeq](path, userProperties, auth))
  }

  val testProps = 
    <job>
      <name>test_job_120</name>
      <parameters>
        <param
          name="input_collection"
          type="collection"
          value="Collection_CW2"/>
      </parameters>
    </job>

  def jobStatus(algId: String) = 
    checkErr(get[NodeSeq](root / "job" / algId / "status", auth))

  def listJobs = 
    checkErr(get[NodeSeq](root / "job" / "status" / "all", auth))

  def jobStdout(jobId: String) =
    checkErr(get[NodeSeq](root / "job" / jobId / "result" / "stdout", auth))

  def jobStderr(jobId: String) = 
    checkErr(get[NodeSeq](root / "job" / jobId / "result" / "stderr", auth))

  def jobDir(jobId: String) =
    checkErr(get[NodeSeq](root / "job" / jobId / "result" / "dir", auth))

  def initialize:Future[Either[String,NodeSeq]] = {
    val credentials = 
      <credentials>
        <username>{username}</username>
        <password>{password}</password>
      </credentials>
    put[NodeSeq,NodeSeq](root / "login", credentials) map {
      case Left(err) => Left("agent login failed: " + err.statusCode)
      case Right(res) if(res.statusCode == 200) => 
        token = res.body \\ "token" text
        // this newline needed due to a scala parsing bug

        Right(res.body)
      case Right(res) => Left("agent login failed: " + res.statusCode)
    }
  }

}
