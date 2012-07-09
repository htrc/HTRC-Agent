
import org.scalatest.WordSpec
import org.scalatest.matchers.{ShouldMatchers, MustMatchers}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

import scala.xml._

import htrcagentclient._
import httpbridge.HttpUtils._

import akka.dispatch.Future

trait Agent extends BeforeAndAfterAll { this: Suite =>

  val agent = new HtrcAgentClient("test-user")
  var initResult: NodeSeq = null
                                      

  override def beforeAll() {
    initResult = now(agent.initialize)
  }

}

class NingAgentSpec extends WordSpec with MustMatchers with ShouldMatchers with Agent {

  import agent._

  "Our play2-mini agent" must {

    "successfully create users" in {
       initResult should be ===
        <agentID>test-user</agentID>
    }
      
    "provide availible algorithms" in {
      (now(listAlgorithms) \ "algorithm").map(_.text) should be ===
        List("factorial", "data_api_test", "htrc_wordcount")
    }

    "provide availible collections" in {
      (now(listCollections) \ "collection").map(_.text) should be ===
        List("numbers")
	}

    "successfully run algorithms" in {
      val res = now(runAlgorithm("factorial"))
      (res \ "status" text) should be === "Prestart"
    }

    "list an agent's algorithms" in {
      Thread.sleep(3000)
      val res = now(listAgentAlgorithms)
      (res \\ "status" text) should be === "Finished"

    }

    "provide algorithm stdout" in {
      (now(algorithmStdout("algId_1_test-user")) \ "stdout").text should be === "120\n"
    }

    "provide algorithm stderr" in {
      pending
    }

    "provide contents of algorithm file output" in {
      pending
    }

    "provide status of polled algorithm" in {
      val res = now(pollAlgorithm("algId_1_test-user"))
      (res \ "status" text) should be === "Finished"
    }
        
  }

}
