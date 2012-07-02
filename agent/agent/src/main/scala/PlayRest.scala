
package htrcagent

import com.typesafe.play.mini._
import play.api.mvc.{ Action, AsyncResult }
import akka.actor.{ ActorSystem, Props, Actor }
import play.api.mvc.Results._
import akka.pattern.ask
import akka.util.Timeout
import akka.util.duration._
import play.api.libs.concurrent._
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._
import scala.collection.mutable.HashMap
import play.api.mvc.RequestHeader
import play.api.mvc.BodyParser._
import play.api.mvc.BodyParsers._
import akka.dispatch.Future
import akka.dispatch.Future._
import akka.dispatch.ExecutionContext
import play.api.libs.concurrent.Akka._

import com.typesafe.config.ConfigFactory

object PlayRest extends Application {

  // timeout for responding to requests
  implicit val timeout = Timeout(5000 milliseconds)

  // this is the base path for agents
  private val pbase = "akka://htrc/user/"

  // the initialization for multiple machines has been disabled

  // the actor system
  private val system = ActorSystem("htrc", ConfigFactory.load.getConfig("htrcsingle"))

  // create a worker node on another jvm
  // val nodeAllocator = system.actorOf(Props[NodeAllocator], "nodeAllocator")

  // test it a bit!
  // for(i <- 1 to 20) yield { ping }
  // def ping = {
  //   val reply = (nodeAllocator ? "hello other jvm")
  //   reply.mapTo[String].map { r =>
  //     println(r)
  //   }
  // }

  // create an actor proxy of the registry
  private val registryActor = system.actorOf(Props[RegistryActor], name = "registryActor")

  // create an actor proxy of solr
  private val solrActor = system.actorOf(Props[SolrActor], name = "solrActor")

  // store a hashmap of what agents exist?
  // remove once I figure out how to do "exists?" on actors
  val agents: HashMap[String,String] = new HashMap

  // create a debug agent so api calls can be made without creating an agent
  system.actorOf(Props(new HtrcAgent(HtrcCredentials("argle", "bargle"))) , name = "debug-agent")
  agents put ("debug-agent", "debug-agent")


  // since the syntax is horrific pimp strings to include a dispatch method
  case class HtrcActorRef(userId: String, agents: HashMap[String,String]) {
    // this just looks up the appropriate agent and sends it the message
    // if the agent doesn't exist, return an error with that fact
    def dispatch(msg: HtrcMessage): Action[play.api.mvc.AnyContent] =
      Action {     
        if (!agents.contains(userId)) {
          BadRequest("agent doesn't exist")
        } else {
            AsyncResult {
              val uf = system.actorFor(pbase + userId) ask msg
              uf.mapTo[scala.xml.NodeSeq].asPromise.map { res =>
                Ok(res) }
            }
          }
      }  
  }
  
  implicit def strToHtrcActorRef(str: String): HtrcActorRef = HtrcActorRef(str, agents)

  // the actual routes
  def route = {

    // Agent creation, TODO : check credentials first
    case PUT(Path(Seg("agent" :: userId :: Nil))) => Action(parse.xml) {  
      implicit request =>
      AsyncResult { 
        implicit val ec = system.dispatcher
        val res: Future[Option[HtrcCredentials]] = Future {
          val raw = request.body
          val credentials:Option[HtrcCredentials] = try {
            val x509 = raw \\ "x509certificate" text
            val privateKey = (raw \\ "privatekey" text)
              if (privateKey.length == 0 || x509.length == 0) {
                None
              } else {
                Some(HtrcCredentials(x509, privateKey))
              }
          } catch {
            case e =>
              println("===> BadRequest: " + raw)
              None
          }
          credentials
        }

        res.mapTo[Option[HtrcCredentials]].asPromise.map { credentials =>
          if(credentials == None)
            BadRequest("malformed credentials")
          else {
            if(!agents.contains(userId)) {
              println("===> User with credentials creating agent: " + credentials)
              system.actorOf(Props(new HtrcAgent(credentials.get)), name = userId)
              agents put (userId, userId)
            }
            Ok(<agentID>{userId}</agentID>)
          }
        }
      }
    }
    
    case GET(Path(Seg("agent" :: userId :: "algorithm" :: "list" :: Nil))) =>
      userId dispatch ListAvailibleAlgorithms

    case GET(Path(Seg("agent" :: userId :: "collection" :: "list" :: Nil))) =>
      userId dispatch ListAvailibleCollections

    case GET(Path(Seg("agent" :: userId :: "algorithm" :: "run" :: algName :: colName :: args :: Nil))) =>
      userId dispatch RunAlgorithm(algName, colName, args)

    case GET(Path(Seg("agent" :: userId :: "algorithm" :: "poll" :: Nil))) =>
      userId dispatch ListAgentAlgorithms
    
    case GET(Path(Seg("agent" :: userId :: "algorithm" :: algId :: "result" :: "console" :: "stdout" :: Nil))) =>
      userId dispatch AlgStdout(algId)

    case GET(Path(Seg("agent" :: userId :: "algorithm" :: algId :: "result" :: "console" :: "stderr" :: Nil))) =>
      userId dispatch AlgStderr(algId)

    case GET(Path(Seg("agent" :: userId :: "algorithm" :: algId :: "result" :: "file" :: filename :: Nil))) => 
      userId dispatch AlgFile(algId, filename)

    case GET(Path(Seg("agent" :: userId :: "algorithm" :: "poll" :: algId :: Nil))) =>
        userId dispatch PollAlg(algId)

    // a put request that expects an xml body, directly available as xml
/*    case PUT(Path(Seg("xml" :: Nil))) => Action(parse.xml) { implicit request =>
      println(request.body)
      
      val wat = Map[String,String](("str" -> "a string"),("num" -> "120"))
      val huh = poForm.bind(wat)
      println(huh.get)
                                                            
      Ok("alright\n")
    }

    // a put request that expects json, parsed into a form object
    case PUT(Path(Seg("tp" :: num :: Nil))) => Action { implicit request =>
      val po = poForm.bindFromRequest.get
      println("successful: " + po.str + " " + po.num)
      Ok("successful: " + po.str + " " + po.num)
    }
                                                       
    case GET(Path("/ping")) => Action {
      // xml.pong is a twirl template! 
      // note: need to fix the weird toString.as("text/xml") stuff
      val http = xml.pong(System.currentTimeMillis.toInt).toString
      Ok(http).as("text/xml")
    }

    // using a path extractor to bind userId
    case GET(Path(Seg("agent" :: userId :: Nil))) => 
      // dispatch is the pimp added above
      userId dispatch Hello(1)

    // post syntax, binds from directly
    case POST(Path("/auth/check")) => Action { implicit request =>
      val po = poForm.bindFromRequest.get
      Ok("str: " + po.str + " num: " + po.num) }
    
    // uses dispatcher, with a post use a form not a message
    // the form is then filled and used as the message
    case POST(Path(Seg( "agent" :: userId :: "posted" :: Nil))) =>
      userId dispatch poForm
      */
  }
  

}

// case class Po(str: String, num: Int)

// curl syntax for testing the service:
// curl -d "str=factorial5is" -d "num=120" localhost:9000/agent/120/posted
