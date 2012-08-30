
// a full refactoring of the Play code

package htrcagent

import httpbridge._
import httpbridge.HttpUtils._

import scala.collection.mutable.HashMap
import scala.concurrent.stm._
import scala.xml._

import akka.dispatch.{Future, ExecutionContext}
import akka.dispatch.Future._
import akka.pattern.ask
import akka.util.Timeout
import akka.util.duration._
import akka.actor.{ActorSystem, Props, Actor}
import akka.routing.RoundRobinRouter

import com.typesafe.play.mini._
import com.typesafe.config.ConfigFactory

import play.api.mvc._
import play.api.mvc.Results._
import play.api.mvc.BodyParser._
import play.api.mvc.BodyParsers._
import play.api.libs.concurrent._
import play.api.libs.concurrent.Akka._


object PlayRest extends Application {

  // system parameters
  implicit val timeout = Timeout(10 seconds)
  private val system = HtrcSystem.system
  implicit val executor = system.dispatcher

  // this helper uses the TypeSafe config system to pull from htrc.conf
  def htrcParam(path: String):String = system.settings.config.getString("htrc."+path)

  // components
  private val registryActor = system.actorOf(Props[RegistryActor].withRouter(
    RoundRobinRouter(nrOfInstances = 64)), name = "registryActor")

  private val portAllocator = system.actorOf(Props[PortAllocator], name = "portAllocator")

  private val oauthUrl = htrcParam("urls.oa2")
  private val oauth2 = new Oauth2(Url(oauthUrl))
  private val oa2DebugUser = htrcParam("auth.oa2.user")
  private val oa2DebugPass = htrcParam("auth.oa2.pass")
  private val ignoreCredentials = htrcParam("auth.ignore_credentials")

  /* agent token management
   * use a STM protected hashmap
   * the keys are strings, the values are agent info bundles
   */

  // this is not just Oauth2Token because there will likely be more info present
  case class AgentInfo(username: String, token: String, expirationTime: Long)
  val agents: Ref[HashMap[String,AgentInfo]] = Ref(new HashMap)
  val usernames: Ref[HashMap[String,String]] = Ref(new HashMap) 
  // username to "active" token
  
  def lookupToken(token: String): Option[AgentInfo] = {
    atomic { implicit t =>
      agents.get.get(token)
    }
  }

  def agentExists(token: String): Boolean = {
    atomic { implicit t =>
      agents.get.contains(token)
    }
  }

  def addAgent(token: Oauth2Token) {
    val info = AgentInfo(token.username, token.token, token.expirationTime)
    atomic { implicit t =>
      agents.transform { _ += (info.token -> info) }
      usernames.transform { _ += (info.username -> info.token) }
    }
  }

  def checkUsername(username: String): Option[String] = {
    atomic { implicit t =>
      usernames.get.get(username)
    }
  }

  // a debug agent that does not require correct authorization
  val debugAgentExists = htrcParam("debug.agent") == true
  val debugToken = 
    if(debugAgentExists) {
      val tok = now(oauth2.authenticate(oa2DebugUser, oa2DebugPass))
      tok match {
        case Left(err) => None
        case Right(tok) => 
          addAgent(tok)
          Some(tok)
      }
    } else {
      None
    }


  // a function to send messages to the appropriate agent 

  // step 1: retrieve the token and check if it is valid
  // step 2: get the agent that the token points to
  // step 3: build the message and send it to the actor
  // step 4: turn the response into a Play AsyncResult

  // error handling: return an either, where left contains error info

  def checkAuth(request: Request[AnyContent]): Either[Elem, AgentInfo] = {
    
    val auth = request.headers.get("Authorization")
    val rawToken = auth.getOrElse("no auth_header")
    val token = rawToken.split(' ').last

    if(token == "auth_header") {
      Left(<error>malformed authorization header: {auth}</error>)
    } else {
      val agentInfo = lookupToken(token)
      if(agentInfo == None) {
        Left(<error>token does not map to agent: {token}</error>)
      } else {
        val info = agentInfo.get
        //if(info.expirationTime < System.currentTimeMillis) {
        if(false) {
          Left(<error>token is expired: {token}</error>)
        } else {
          Right(info)
        }
      }
    }
  }

  def toAgent(agentInfo: AgentInfo, msg: => HtrcMessage): Either[Elem, Future[NodeSeq]] = {
    
    val agent = system.actorFor("akka://htrc/user/" + agentInfo.username)
    
    if(false) {
      // todo : check that looking up the agent was successful
      Left(<error>no agent actor associated with token: {agentInfo.token}</error>)
    } else {
      Right((agent ask msg).mapTo[NodeSeq])
    }

  }

  // note : checkAuth is not currently run asynchronously as it should be trivial
  def dispatch(msg: NodeSeq => HtrcMessage): Action[AnyContent] = {
    Action { implicit request =>
      val agentInfo = checkAuth(request)
      agentInfo match {
        case Left(error) => 
          BadRequest(error)
        case Right(agentInfo) =>
          val input = 
            request.body.asXml match {
              case Some(xml) => xml
              case _ => <error>blank body</error>
            }                
          val computedMessage = msg(input)
          val f = toAgent(agentInfo, computedMessage)
          f match {
            case Left(error) =>
              BadRequest(error)
            case Right(response) => 
              AsyncResult {
                response.mapTo[NodeSeq].asPromise.map { res =>
                  Ok(res)
                }
              }
          }
      }
    }
  }

  // a second form allows dispatch to be called with or without the
  // 'request =>'
  def dispatch(msg: => HtrcMessage): Action[AnyContent] = {
    dispatch { body => msg }
  }
  
  def login: Action[NodeSeq] = {
    Action(BodyParsers.parse.xml) {
      implicit request =>
        if(htrcParam("auth.ignore_credentials") == "false") { 
          val isXml = 
            request.contentType match {
              case Some(str) => str.contains("text/xml")
              case None => false
            }
          if(!isXml) {
            BadRequest(<error>login content type not xml: {request.body}</error>)
          } else {
            val username = request.body \ "username" text
            val password = request.body \ "password" text
            // this newline needed due to a scala parser bug

            if(username == "" || password == "") {
              BadRequest(<error>malformed login credentials: {request.body}</error>) 
            } else {
              // check if the user is already logged in here
              checkUsername(username) match {
                case Some(token) =>
                  val tokenFuture = oauth2.authenticate(username, password)
                  AsyncResult {
                    tokenFuture.asPromise.map {
                      case Left(err) => BadRequest(<error>{err}</error>)
                      case Right(oa2token) =>
                        Ok(<agent><agentID>{username}</agentID><token>{token}</token></agent>)
                    }
                  }
                case None =>
                  val tokenFuture = oauth2.authenticate(username, password)
                     AsyncResult {
                       createAgent(tokenFuture).asPromise.map {
                         case Left(err) => BadRequest(<error>{err}</error>)
                         case Right(response) =>
                           Ok(response)
                       }
                     }
              }
            }
          }
        } else {
          if(debugToken != None) {
            val token = debugToken.get
            Ok(<agent><agentID>{token.username}</agentID><token>{token.token}</token></agent>)
           } else {
             BadRequest(<error>ignore_credentials is set to true but there is no debug agent</error>)
           }
        }
    }
  }

  // todo : refactor this into another actor to get rid of race conditions
  // todo : check that agent creation succeeds
  def createAgent(tokenFuture: Future[Either[String,Oauth2Token]]): Future[Either[String,NodeSeq]] = {

    tokenFuture map {
      case Left(err) => Left(err)
      case Right(token) =>
        if(!agentExists(token.token)) {
          system.actorOf(Props(new HtrcAgent(token)), name = token.username)
          addAgent(token)
        }
       Right(
        <agent>
          <agentID>{token.username}</agentID>
          <token>{token.token}</token>
        </agent>
       )
      
    }
  }

  // nonsense time!
  // scala pattern matching is broken, so split routing into two functions combined

  type In = play.api.mvc.RequestHeader
  type Out = play.api.mvc.Handler

  def route = foo orElse bar



  val foo: PartialFunction[In,Out] = {
    case PUT(Path(Seg("agent" :: "login" :: Nil))) => 
      login

    case GET(Path(Seg("agent" :: "download" :: "collection" :: name :: Nil))) =>
      dispatch { DownloadCollection(name) }

    case PUT(Path(Seg("agent" :: "upload" :: "collection" :: Nil))) =>
      dispatch { body => UploadCollection(body) }

    case PUT(Path(Seg("agent" :: "modify" :: "collection" :: Nil))) =>
      dispatch { body => ModifyCollection(body) }
   
    case GET(Path(Seg("agent" :: "algorithm" :: "list" :: Nil))) => 
      dispatch { ListAvailibleAlgorithms }
    
    case GET(Path(Seg("agent" :: "collection" :: "list" :: Nil))) => 
      dispatch { ListAvailibleCollections }

    case GET(Path(Seg("agent" :: "algorithm" :: "details" :: algName :: Nil))) =>
      dispatch { AlgorithmDetails(algName) }
  }

  val bar:PartialFunction[In,Out] = {
    // the incoming body is xml with the arguments
    case PUT(Path(Seg("agent" :: "algorithm" :: "run" :: algName :: Nil))) =>
      dispatch { body => RunAlgorithm(algName, body) }

    case GET(Path(Seg("agent" :: "job" :: algId :: "status" :: Nil))) =>
      dispatch { AlgorithmStatusRequest(algId) }

    case GET(Path(Seg("agent" :: "job" :: algId :: "result" :: "stdout" :: Nil))) =>
      dispatch { AlgorithmStdoutRequest(algId) }

    case GET(Path(Seg("agent" :: "job" :: algId :: "result" :: "stderr" :: Nil))) =>
      dispatch { AlgorithmStderrRequest(algId) }

    case GET(Path(Seg("agent" :: "job" :: algId :: "result" :: "dir" :: Nil))) =>
      dispatch { JobDirRequest(algId) }

    case GET(Path(Seg("agent" :: "job" :: "status" :: "all" :: Nil))) =>
      dispatch { AllJobStatusRequest }

  }

}
