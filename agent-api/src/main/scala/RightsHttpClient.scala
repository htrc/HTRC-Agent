package htrc.agent

// class to send requests to the Rights API

import scala.concurrent.Future
import spray.http._
import HttpMethods._

import akka.io.IO
import spray.can.Http
import scala.concurrent.ExecutionContext
import HttpHeaders._
import MediaTypes._

object RightsHttpClient extends HtrcHttpClient {
  def queryRights(query: String, method: HttpMethod, token: String,
    acceptContentType: String, body: Option[String] = None):
      Future[HttpResponse] = {

    val rightsUrl = "https://htrc4.pti.indiana.edu:10443/rights-api/"
    val uri = rightsUrl + query

    val contentType = `application/json`

    val headers = List(RawHeader("Accept", acceptContentType),
      RawHeader("Authorization", "Bearer " + token))
    val httpRequest =
      body map { b =>
        HttpRequest(method = method, uri = uri,
          entity = HttpEntity(contentType, b), headers = headers)
      } getOrElse {
        HttpRequest(method = method, uri = uri, headers = headers)
      }

    val response = queryService(uri, httpRequest)

    log.debug("RIGHTS_QUERY\tTOKEN: {}\tQUERY: {}", token, query)
    // printResponse(response)

    response
  }
}
