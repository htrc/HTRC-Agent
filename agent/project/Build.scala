
import sbt._
import Keys._

object AgentBuild extends Build {

  lazy val root = Project(id = "rest", base = file("."), settings = Project.defaultSettings).settings(mainClass in (Compile, run) := Some("play.core.server.NettyServer"))  

  lazy val worker = Project(id = "worker",
                            base = file("."))

}
