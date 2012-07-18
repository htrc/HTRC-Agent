
package htrcagent

import akka.actor.Actor
import akka.actor.Actor._
import scala.collection.mutable.HashMap
import scala.xml._
import dispatch._

trait Solr {

  val http = new Http()
  val root = :/("coffeetree.cs.indiana.edu", 9994)
  val queryRoot = root / "solr" / "select" / ""

  def query(params: Traversable[(String, String)]): Elem = {
    try {
      http(queryRoot <<? params <> { res => res })
    } catch {
      case e => <error>solr error of some sort</error>
    }
  }

  def attributeValueEquals(value: String)(node: Node) = {
    node.attributes.exists(_.value.text == value)
  }

  def findAttribute(attr: String)(nodes: NodeSeq) = {
    nodes \\ "_" filter(attributeValueEquals(attr))
  }

  def findNamedValue(value: String)(nodes: NodeSeq) = {
    (nodes \\ ("@" + value)).text
  }

  def volumeIds(nodes: NodeSeq): Seq[String] = {
    findAttribute("id")(nodes) map { _.text }
  }

}

class SolrActor extends Actor with Solr {

  def receive = {

    case SolrQuery(params) =>
      println("About to query solr: " + params)
      sender ! query(params)
      
  }

}
