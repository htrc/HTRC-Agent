
import sbt._
import Keys._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions }


object AgentBuild extends Build {

  lazy val root = Project(id = "root", base = file(".")) aggregate(rest, client, http)

  lazy val rest = Project(
    id = "rest", 
    base = file("agent"), 
    settings = Project.defaultSettings ++ AkkaKernelPlugin.distSettings ++ Seq(
      distJvmOptions in Dist := "-Xms 1024M -Xmx12G",
      outputDirectory in Dist := file("target/rest-dist"))).settings(mainClass in (Compile, run) := Some("play.core.server.NettyServer")).dependsOn("http")

  lazy val worker = Project(id = "worker",
                            base = file("agent")).dependsOn("http").dependsOn("rest")

  lazy val client = Project(id = "client",
                            base = file("client")).dependsOn("http")

  lazy val http = Project(id = "http",
                          base = file("http"))

  

}
