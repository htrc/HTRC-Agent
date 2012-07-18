
package htrcagent

import httpbridge._

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
import play.api.mvc.Request
import scala.collection.mutable.HashMap
import play.api.mvc.RequestHeader
import play.api.mvc.BodyParser._
import play.api.mvc.BodyParsers._
import akka.dispatch.Future
import akka.dispatch.Future._
import akka.dispatch.ExecutionContext
import play.api.libs.concurrent.Akka._
import play.api.mvc.AnyContent
import scala.xml._
import play.api.libs.json._
import play.api.libs.json.Json._

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

  // connection to the oauth2 server
  private val oauthUrl = "https://129-79-49-119.dhcp-bl.indiana.edu:25443/oauth2/token"
  private val oauth2 = new Oauth2(Url(oauthUrl))

  // store a hashmap of what agents exist?
  // remove once I figure out how to do "exists?" on actors
  val agents: HashMap[String,Oauth2Token] = new HashMap

  // create a debug agent so api calls can be made without creating an agent
  val debugToken = oauth2.now(oauth2.authenticate("yim","yim"))
  system.actorOf(Props(new HtrcAgent(debugToken)) , name = "debug-agent")
  agents put ("debug-agent", debugToken)


  // since the syntax is horrific pimp strings to include a dispatch method
  // TODO : Anything but this. Certainly implicit overuse.

  case class HtrcActorRef(userId: String, agents: HashMap[String,Oauth2Token]) {
    // this just looks up the appropriate agent and sends it the message
    // if the agent doesn't exist, return an error with that fact
    def dispatch(msg: HtrcMessage): Action[play.api.mvc.AnyContent] =
      Action { implicit request =>    

        val auth = request.headers.get("Authorization")

        if (!agents.contains(userId)) {
          BadRequest("agent doesn't exist")
        } else if(auth.getOrElse("no_authorization_header") != "Bearer " + agents(userId).token) {
          println("===> invalid oauth2 token on agent: " + userId)
          BadRequest("invalid oauth2 token")
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

  // attempt 2 at some good syntax for dispatching to agents

  implicit val request: Request[AnyContent] = null

  def dispatch2(agentId: String)(body: => HtrcMessage): Action[AnyContent] = {
    Action { implicit request =>
      
      println(request)

      val auth = request.headers.get("Authorization")

      if(!agents.contains(agentId)) {
        BadRequest("agent does not exist")
      } else if(auth.getOrElse("no_token") != "Bearer " + agents(agentId).token) {
        BadRequest("invalid oauth2 token")
      } else {
        AsyncResult {
          val agentRef = system.actorFor(pbase + agentId)
          val msg = body
          (agentRef ask msg).mapTo[NodeSeq].asPromise.map { res =>
            Ok(res) }
        }
      }
    }
  }


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

        val token: Future[Oauth2Token] = res.mapTo[Option[HtrcCredentials]] flatMap { credentials =>
          oauth2.authenticate("yim", "yim")
        }

        token.asPromise.map { token =>
            
            if(!agents.contains(userId)) {
                println("===> User with credentials creating agent: " + token)
              try {
                system.actorOf(Props(new HtrcAgent(token)), name = userId)
              } catch {
                case e => println(e)
              }
              agents put (userId, token)
            }
            Ok(<agent><agentID>{userId}</agentID><token>{token.token}</token></agent>)
            
//          }
                                                        }
      }
    }
    
    case GET(Path(Seg("agent" :: userId :: "algorithm" :: "list" :: Nil))) =>
      userId dispatch ListAvailibleAlgorithms
     

    case GET(Path(Seg("agent" :: userId :: "collection" :: "list" :: Nil))) =>
      userId dispatch ListAvailibleCollections

    case GET(Path(Seg("agent" :: userId :: "algorithm" :: "run" :: algName :: colName :: args :: Nil))) =>
      userId dispatch RunAlgorithm(algName, colName, args)

    case GET(Path(Seg("agent" :: userId :: "run" :: "algorithm" :: Nil))) => 

      dispatch2(userId) { 
        request.body.asJson.map(js:JsValue => js.as[RunAlgorithmJson]) 
      }
      

//      Action(parse.json).as[RunAlgorithmJson] { implicit request =>

//          implicit val ec = system.dispatcher

          // get json and convert to case class
//          println(request.body)

          // send the case class as a message to an agent

//          Ok(<m>got json</m>)


  //    }

        

    case GET(Path(Seg("agent" :: userId :: "algorithm" :: "poll" :: Nil))) =>
      userId dispatch ListAgentAlgorithms
    
    case GET(Path(Seg("agent" :: userId :: "algorithm" :: algId :: "result" :: "stdout" :: Nil))) =>
      userId dispatch AlgStdout(algId)

    case GET(Path(Seg("agent" :: userId :: "algorithm" :: algId :: "result" :: "stderr" :: Nil))) =>
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
