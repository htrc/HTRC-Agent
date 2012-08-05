
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
  implicit val timeout = Timeout(5000 milliseconds)
  private val system = ActorSystem("htrc", ConfigFactory.load.getConfig("htrcsingle"))
  implicit val executor = system.dispatcher

  def htrcParam(path: String):String = system.settings.config.getString("htrc."+path)

  // components
  private val registryActor = system.actorOf(Props[RegistryActor], name = "registryActor")
  private val solrActor = system.actorOf(Props[SolrActor], name = "solrActor")

  private val oauthUrl = htrcParam("urls.oa2")
  private val oauth2 = new Oauth2(Url(oauthUrl))
  private val oa2DebugUser = htrcParam("auth.oa2.user")
  private val oa2DebugPass = htrcParam("auth.oa2.pass")
  private val ignoreCredentials = htrcParam("auth.ignore_credentials")

  /* agent token management
   * use a STM protected hashmap
   * the keys are strings, the values are agent info bundles
   */

  case class AgentInfo(username: String, token: String, expirationTime: Long)
  val agents: Ref[HashMap[String,AgentInfo]] = Ref(new HashMap)
  
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
    }
  }

  // a debug agent that does not require correct authorization
  val debugAgentExists = htrcParam("debug.agent") == true
  val debugToken = 
    if(debugAgentExists) {
      val tok = now(oauth2.authenticate(oa2DebugUser, oa2DebugPass))
      addAgent(tok)
      Some(tok)
    } else {
      None
    }

  // a function to send messages to the appropriate agent 

  // step 1: the token
  // step 2: create message
  // step 3: send to actor and lookup result

  // error handling: if something is wrong need to create an "error" response
  // do so by having the helper functions return an either
  // left is a play promise / response with the error
  // right is a future with the new information 

  // can request be implicit?
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
        if(info.expirationTime < System.currentTimeMillis) {
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

  // todo : replace AnyContent with NodeSeq
  def dispatch(msg: => HtrcMessage): Action[play.api.mvc.AnyContent] = {
    Action { implicit request =>
      val agentInfo = checkAuth(request)
      agentInfo match {
        case Left(error) => 
          //Future { error }.mapTo[NodeSeq].asPromise.map { e => BadRequest(e) }
          BadRequest(error)
        case Right(agentInfo) =>
          val f = toAgent(agentInfo, msg)
        f match {
          case Left(error) =>
            //Future { error }.mapTo[NodeSeq].asPromise.map { e => BadRequest(e) }
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

  // note : the key assumption I've made is that the request is available
  //        to the function that generates the message 
  //        This could be done by requiring a second function that generates
  //        input to the first?
    
  // todo : switch AnyContent to xml
  def login: Action[NodeSeq] = {
    Action(BodyParsers.parse.xml) {
      implicit request =>
        if(htrcParam("auth.ignore_credentials") == "false") { 
          if(false) {
            BadRequest(<error>login content type not xml</error>)         
          } else {
            val username = request.body \ "username" text
            val password = request.body \ "password" text

            if(username == "" || password == "") {
              BadRequest(<error>malformed login credentials: {request.body}</error>) 
            } else {
              val tokenFuture = oauth2.authenticate(username, password).mapTo[Oauth2Token]
              AsyncResult {
                createAgent(tokenFuture).mapTo[NodeSeq].asPromise.map { response =>
                  Ok(response)
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

  // todo : check that agent creation succeeds
  def createAgent(tokenFuture: Future[Oauth2Token]): Future[NodeSeq] = {
    tokenFuture map { token =>
      if(!agentExists(token.token)) {
        system.actorOf(Props(new HtrcAgent(token)), name = token.username)
        addAgent(token)
      }
      <agent>
        <agentID>{token.username}</agentID>
        <token>{token.token}</token>
      </agent>
    }
  }
              
  def route = {

    case PUT(Path(Seg("agent" :: "login" :: Nil))) => 
      login
   
    case GET(Path(Seg("agent" :: "algorithm" :: "list" :: Nil))) => 
      dispatch { ListAvailibleAlgorithms }
    
    case GET(Path(Seg("agent" :: "collection" :: "list" :: Nil))) => 
      dispatch { ListAvailibleCollections }
    
    case GET(Path(Seg("agent" :: "algorithm" :: "run" :: algName :: colName :: args :: Nil))) => 
      dispatch { RunAlgorithm(algName, colName, args) }
    
    case GET(Path(Seg("agent" :: "algorithm" :: "poll" :: Nil))) => 
      dispatch { ListAgentAlgorithms }
    
    case GET(Path(Seg("agent" :: "algorithm" :: algId :: "result" :: "stdout" :: Nil))) => 
      dispatch { AlgStdout(algId) }

    case GET(Path(Seg("agent" :: "algorithm" :: algId :: "result" :: "stderr" :: Nil))) =>
      dispatch { AlgStderr(algId) }
    
    case GET(Path(Seg("agent" :: "algorithm" :: algId :: "result" :: "file" :: filename :: Nil))) => 
      dispatch { AlgFile(algId, filename) }

    case GET(Path(Seg("agent" :: "algorithm" :: "poll" :: algId :: Nil))) => 
      dispatch { PollAlg(algId) }

  }

}
