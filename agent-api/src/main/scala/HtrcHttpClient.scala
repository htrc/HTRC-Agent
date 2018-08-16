package htrc.agent

// class to send requests to other HTRC services; requests use mutual TLS
// authentication

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

import javax.net.ssl.SSLContext
import org.apache.http.ssl.SSLContexts
import HttpHeaders._
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIServerName
import scala.collection.JavaConverters._

class HtrcHttpClient {
  implicit val timeout = Timeout(5 seconds)
  implicit val system = HtrcSystem.system
  import system.dispatcher
  val log = Logging(system, "htrc-http-client")

  // "futureTimeout: Timeout = 60.seconds" was also an implicit param
  def mySendReceive(request: HttpRequest)(implicit uri: spray.http.Uri,
    ec: ExecutionContext,
    sslContext: SSLContext): Future[HttpResponse] = {

    val uriHost = uri.authority.host.address
    val uriPort = uri.authority.port
    val hostConnPort = if (uriPort == 0) 443 else uriPort

    implicit val clientSSLEngineProvider = ClientSSLEngineProvider { _ =>
      val engine = sslContext.createSSLEngine( )
      engine.setEnabledProtocols(Array("TLSv1.1", "TLSv1.2"))

      // set the SNI, since it is expected by nginx
      val sslParams = engine.getSSLParameters()
      val serverNames = sslParams.getServerNames()
      val sniServerName: SNIServerName = new SNIHostName(uriHost)
      sslParams.setServerNames(List(sniServerName).asJava)
      engine.setSSLParameters(sslParams)

      engine.setUseClientMode( true )
      engine
    }

    // log.debug("HTRC_HTTP_QUERY: uri = {}, uri.authority.host.address = {}, uri.authority.port = {}, hostConnPort = {}", uri, uriHost, uriPort, hostConnPort)

    for {
      // Http.HostConnectorInfo(connector, _) <- IO(Http) ? Http.HostConnectorSetup( uri.authority.host.address, port = uri.authority.port, sslEncryption = true )
      Http.HostConnectorInfo(connector, _) <- IO(Http) ? Http.HostConnectorSetup( uriHost, port = hostConnPort, sslEncryption = true )
      response <- connector ? request
    } yield response match {
      case x: HttpResponse => x
      case x: HttpResponsePart => sys.error("sendReceive doesn't support chunked responses, try sendTo instead")
      case x: Http.ConnectionClosed => sys.error("Connection closed before reception of response: " + x)
      case x => sys.error("Unexpected response from HTTP transport: " + x)
    }
  }

  def queryService(uri: String, httpRequest: HttpRequest): Future[HttpResponse] = {
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

      val keystore = HtrcConfig.keystoreForOutgoingReqs;
      val keystorePsswd = HtrcConfig.keystorePasswd;
      val ctx =
        SSLContexts.custom()
          .loadKeyMaterial(new File(keystore), keystorePsswd.toCharArray(), keystorePsswd.toCharArray())
	  .build();
      ctx
    }

    val response = mySendReceive(httpRequest)

    response
  }
}
