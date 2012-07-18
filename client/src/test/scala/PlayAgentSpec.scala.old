
import org.scalatest.WordSpec
import org.scalatest.matchers.{ShouldMatchers, MustMatchers}
import dispatch._
import scala.xml._

class PlaySpec extends WordSpec with MustMatchers with ShouldMatchers {

  import AgentClient._

  "Our play2-mini agent" must {

    "successfully create users" in {
      createAgent("test-user", "fakex509", "fakePrivate") should be ===
        <agentID>test-user</agentID>
    }
      
    "provide availible algorithms" in {
      (listAvailibleAlgorithms("test-user") \ "algorithm").map(_.text) should be ===
        List("factorial", "data_api_test")
    }

    "provide availible collections" in {
      (listAvailibleCollections("test-user") \ "collection").map(_.text) should be ===
        List("numbers")
	}

    "successfully run algorithms" in {
      val res = runAlgorithm("test-user", "factorial", "5", "optimized")
      (res \ "status" text) should be === "Prestart"
    }

    "list an agent's algorithms" in {
      Thread.sleep(3000)
      val res = listAgentAlgorithms("test-user")
      (res \\ "status" text) should be === "Finished"

    }

    "provide algorithm stdout" in {
      (algStdout("test-user", "algId_1_test-user").head \ "stdout").text should be === "120\n"
    }

    "provide algorithm stderr" in {
      pending
    }

    "provide contents of algorithm file output" in {
      pending
    }

    "provide status of polled algorithm" in {
      val res = pollAlg("test-user", "algId_1_test-user")
      (res \ "status" text) should be === "Finished"
    }
        
  }

}
