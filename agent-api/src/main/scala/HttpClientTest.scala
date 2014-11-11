
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

// An example actor that uses the Spray asynchronous http client

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
import scala.util.{Success, Failure}

object HttpClientTest {

  implicit val timeout = Timeout(5 seconds)
  implicit val system = HtrcSystem.system
  val log = Logging(system, "http-client-test")


  val ioBridge = IOExtension(system).ioBridge()

  val httpClient = system.actorOf(Props(new HttpClient(ioBridge)), "http-client")

  // Example 1
  def exampleOne() {

    val conduit = system.actorOf(
      props = Props(new HttpConduit(httpClient, "google.com", 80)),
      name = "http-conduit"
    )

    val pipeline = HttpConduit.sendReceive(conduit)

    val responseFuture = pipeline(HttpRequest(method = GET, uri = "/")).mapTo[HttpResponse]
    responseFuture onComplete {
      case Success(response) =>
        log.info(
          """|Response for GET request to github.com:                                   
          |status : {}                                                               
          |headers: {}                                                               
          |body   : {}""".stripMargin,
          response.status.value, response.headers.mkString("\n  ", "\n  ", ""), response.entity.asString)
      case Failure(error) =>
        log.error(error.toString)
    }
  }

}
