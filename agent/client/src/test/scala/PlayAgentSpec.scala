
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
      listAvailibleAlgorithms("test-user") should be ===
        <availibleAlgorithms>
          <algorithm>factorial</algorithm>
        </availibleAlgorithms>
    }

    "provide availible collections" in {
      listAvailibleCollections("test-user") should be ===
        <collections>
          <collection>5</collection>
        </collections>
	}

    "successfully run algorithms" in {
      val res = runAlgorithm("test-user", "factorial", "5", "optimized")
      (res \ "status" text) should be === "Prestart"
    }

    "list an agent's algorithms" in {
      pending
    }

    "provide algorithm stdout" in {
      Thread.sleep(500)
      algStdout("test-user", "algId_1_test-user") should be ===
        <algorithm>
          <id>algId_1_test-user</id>
          <stdout>{Text("120\n")}</stdout>
        </algorithm>
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
