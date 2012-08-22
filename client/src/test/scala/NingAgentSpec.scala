
import org.scalatest.WordSpec
import org.scalatest.matchers.{ShouldMatchers, MustMatchers}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

import scala.xml._

import htrcagentclient._
import httpbridge.HttpUtils._

import akka.dispatch.Future

trait Agent extends BeforeAndAfterAll { this: Suite =>

  val agent = new HtrcAgentClient()
  var initResult: NodeSeq = null
                                      

  override def beforeAll() {
    initResult = now(agent.initialize) match {
      case Left(err) => <error>{err}</error>
      case Right(res) => res
    }
  }

}

class NingAgentSpec extends WordSpec with MustMatchers with ShouldMatchers with Agent {

  import agent._

  "Our play2-mini agent" must {

    "successfully create users" in {
       (initResult \\ "agentID").text should be === "drhtrc"
    }
      
    "provide availible algorithms" in {
      (now(listAlgorithms).right.get \ "algorithm").map(_.text) should be ===
        List("factorial", "data_api_test", "htrc_wordcount")
    }

    "provide availible collections" in {
       (now(listCollections).right.get \\ "e" find { e => (e \ "@key" text) == "name" }).get.text should be === "An elaborate and clever collection name"
	}

    "upload collections" in {
      (now(uploadCollection(testCollection)).right.get \\ "collection" text) should be ===
        "An elaborate and clever collection name"
    }

    "successfully run algorithms" in {
      val res = now(runAlgorithm("factorial")).right.get
      (res \ "status" text) should be === "Prestart"
    }

    "list an agent's algorithms" in {
      Thread.sleep(3000)
      val res = now(listAgentAlgorithms).right.get
      (res \\ "status" text) should be === "Finished"

    }

    "provide algorithm stdout" in {
      (now(algorithmStdout("algId_1_drhtrc")).right.get \ "stdout").text should be === "120\n"
    }

    "provide algorithm stderr" in {
      pending
    }

    "provide contents of algorithm file output" in {
      pending
    }

    "provide status of polled algorithm" in {
      val res = now(pollAlgorithm("algId_1_drhtrc")).right.get
      (res \ "status" text) should be === "Finished"
    }
        
  }

}
