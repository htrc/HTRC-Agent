
import sbt._
import Keys._

object AgentBuild extends Build {

  lazy val root = Project(id = "root", base = file(".")) aggregate(rest, client, http)

  lazy val rest = Project(
    id = "rest", 
    base = file("agent"), 
    settings = Project.defaultSettings).settings(mainClass in (Compile, run) := Some("play.core.server.NettyServer")).dependsOn("http")

  lazy val worker = Project(id = "worker",
                            base = file("agent")).dependsOn("http").dependsOn("rest")

  lazy val client = Project(id = "client",
                            base = file("client")).dependsOn("http")

  lazy val http = Project(id = "http",
                          base = file("http"))

  

}
