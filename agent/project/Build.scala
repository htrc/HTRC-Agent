
import sbt._
import Keys._

object AgentBuild extends Build {

  lazy val root = Project(id = "root", base = file("."))

  lazy val rest = Project(id = "rest", base = file("agent"), settings = Project.defaultSettings).settings(mainClass in (Compile, run) := Some("play.core.server.NettyServer")).dependsOn("http").dependsOn("root")

  lazy val worker = Project(id = "worker",
                            base = file("agent")).dependsOn("http").dependsOn("rest").dependsOn("root")

  lazy val client = Project(id = "client",
                            base = file("client")).dependsOn("http").dependsOn("root")

  lazy val http = Project(id = "http",
                          base = file("http")).dependsOn("root")

}
