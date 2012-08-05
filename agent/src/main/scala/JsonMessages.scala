
// case class forms of Json information

package htrcagent

import play.api.libs.json.Json._
import play.api.libs.json._

case class RunAlgorithmJson(name: String, agentId: String) extends HtrcMessage

object RunAlgorithmJson {
  implicit object RaReads extends Format[RunAlgorithmJson] {
    def reads(json: JsValue) = RunAlgorithmJson(
      (json \ "name").as[String],
      (json \ "agentId").as[String])
    def writes(ts: RunAlgorithmJson) = JsObject(Seq(
      "name" -> JsString(ts.name),
      "agentId" -> JsString(ts.agentId)))
  }
}

