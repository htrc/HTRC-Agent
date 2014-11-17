
/*
#
# Copyright 2013 The Trustees of Indiana University
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
*/


package htrc.agent

import scala.concurrent.Future
import akka.actor._
import spray.can.client.HttpClient
import spray.client.HttpConduit
import spray.io._
import spray.util._
import spray.http._
import HttpMethods._
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.{ ActorSystem, Props, Actor }
import HttpMethods._
import akka.pattern.ask
import HttpConduit._
import HttpClient._
import akka.event.Logging
import akka.event.slf4j.Logger
import scala.util.{Success, Failure}
import scala.xml._
import HtrcConfig._
import MediaTypes._

object IdentityServerClient {

  // the usual setup
  implicit val timeout = Timeout(5 seconds)
  implicit val system = HtrcSystem.system
  val log = Logging(system, "identity-server-http-client")
  val auditLog = Logger("audit")

  // initialize the bridge to the IO layer used by Spray
  val ioBridge = IOExtension(system).ioBridge()

  def queryClientCredentialsToken(): Future[HttpResponse] = {

    val root = idServerHost
    val path = idServerTokenEndPoint
    val port = idServerPort

    val method = POST

    // create a client to use
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

    // now we define a conduit, which is a use of the client
    val conduit = system.actorOf(
      props = Props(new HttpConduit(httpClient, root, port, sslEnabled=true))
    )
    
    val pipeline: HttpRequest => Future[HttpResponse] = (
      sendReceive(conduit)
    )

    val contentType = `application/x-www-form-urlencoded`

    val formDataStr = 
      createFormDataForCCTokenQuery(agentClientId, agentClientSecret)

    // val formData = FormData(Map("client_id" -> clientId, 
    //                             "client_secret" -> clientSecret, 
    //                             "grant_type" -> "client_credentials"));

    val response = 
      pipeline(HttpRequest(method = method, uri = path, 
                           entity = HttpBody(contentType, formDataStr))).mapTo[HttpResponse]

    log.debug("IS_CLIENT_CRED_TOKEN_QUERY sent.")

    response
  }

  def getClientCredentialsToken(): Future[String] = {
    val q = queryClientCredentialsToken()
    q map { response => 
      if (response.status.isSuccess) {
        val result = extractAccessToken(response.entity.asString)
        log.debug("IS_CLIENT_CRED_TOKEN_QUERY: response = {}, token = {}",
                  response.entity.asString, result)
        result
      }
      else {
        log.debug("IS_CLIENT_CRED_TOKEN_QUERY: ERROR, response.status = {}", 
                  response.status)
        null
      }
    }
  }

  def createFormDataForCCTokenQuery(clientId: String, 
                                    clientSecret: String): String = {
    clientIdFormFieldName + "=" + clientId + "&" +
    clientSecretFormFieldName + "=" + clientSecret + "&" + 
    grantTypeFormFieldName + "=" + clientCredentialsGrantType
  }

  // extracts the access token from a valid token response string sent from
  // the identity server; for example, a valid response to a query for a
  // client credentials type token is
  // {"token_type":"bearer","expires_in":43200,"access_token":"..."}
  def extractAccessToken(tokenResponse: String): String = {
    val i = tokenResponse.indexOf(accessTokenFieldName)
    // the response contains a substring of the form
    // "access_token":"<token>", including quotes
    val delimiter = "\":\""
    val startIndex = i + accessTokenFieldName.length + delimiter.length
    if (i >= 0) {
      // obtain token enclosed within "", as shown above
      tokenResponse.substring(startIndex, 
                              tokenResponse.indexOf("\"", startIndex))
    }
    else ""
  }
}
