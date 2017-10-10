package htrc.agent

// class to send requests to other HTRC services

import scala.concurrent.duration._
import scala.concurrent.Future
import akka.util.Timeout
import akka.pattern.ask
import akka.event.Logging
import akka.event.slf4j.Logger
import javax.net.ssl.SSLContext
import org.apache.http.ssl.SSLContexts
import akka.io.IO
import spray.can.Http
import scala.concurrent.ExecutionContext
import spray.http._
import spray.io.ClientSSLEngineProvider
import java.io.File

object HtrcHttpClient {
  implicit val timeout = Timeout(5 seconds)
  implicit val system = HtrcSystem.system
  import system.dispatcher
  val log = Logging(system, "htrc-http-client")

  // "futureTimeout: Timeout = 60.seconds" was also an implicit param
  def mySendReceive(request: HttpRequest)(implicit uri: spray.http.Uri,
    ec: ExecutionContext,
    sslContext: SSLContext = SSLContext.getDefault): Future[HttpResponse] = {

    implicit val clientSSLEngineProvider = ClientSSLEngineProvider { _ =>
      val engine = sslContext.createSSLEngine( )
      engine.setUseClientMode( true )
      engine
    }

    for {
      Http.HostConnectorInfo(connector, _) <- IO(Http) ? Http.HostConnectorSetup( uri.authority.host.address, port = uri.authority.port, sslEncryption = true )
      response <- connector ? request
    } yield response match {
      case x: HttpResponse => x
      case x: HttpResponsePart => sys.error("sendReceive doesn't support chunked responses, try sendTo instead")
      case x: Http.ConnectionClosed => sys.error("Connection closed before reception of response: " + x)
      case x => sys.error("Unexpected response from HTTP transport: " + x)
    }
  }

  def query(uri: String, httpRequest: HttpRequest): Future[HttpResponse] = {
    implicit val serviceUri = Uri(uri)
      // serviceUri has type spray.http.Uri, and is required as an implicit
      // param to mySendReceive
    implicit val sslContext = {
      /*
      val initTrustManagers = Array(permissiveTrustManager)
      val ctx = SSLContext.getInstance("TLS")
      ctx.init(null, initTrustManagers, new SecureRandom())
      ctx
       */

      val keystore = "/home/drhtrc/rights-api/certificates/htrc4-client.keystore";
      val keystorePsswd = "WPR-2A4-c5g-5hc";
      val ctx =
        SSLContexts.custom()
          .loadKeyMaterial(new File(keystore), keystorePsswd.toCharArray(),
	    keystorePsswd.toCharArray())
	  .build();
      log.debug("HTRC_HTTP_QUERY: ctx.getProtocol = {}", ctx.getProtocol)
      ctx
    }

    // and now finally make a request
    val response = mySendReceive(httpRequest)

    response
  }
}
