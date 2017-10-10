package htrc.agent

// class to send requests to the Rights API

object RightsHttpClient {
  def queryRights(query: String, method: HttpMethod, token: String,
    acceptContentType: String, body: Option[String] = None):
      Future[HttpResponse] = {

    val rightsUrl = "https://htrc5.pti.indiana.edu:10443/rights-api/"
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

    val response = HtrcHttpClient.mySendReceive(uri, httpRequest)

    log.debug("RIGHTS_QUERY\tTOKEN: {}\tQUERY: {}", token, query)
    // printResponse(response)

    response
  }
}
