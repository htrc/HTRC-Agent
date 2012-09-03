
name := "htrc-agent-components"

version := "0.0.5"

scalaVersion := "2.9.2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ~= { seq =>
  val dispatchVers = "0.8.8"
  seq ++ Seq(
    "net.databinder" %% "dispatch-core" % dispatchVers,
    "net.databinder" %% "dispatch-http" % dispatchVers,
    "net.databinder" %% "dispatch-nio" % dispatchVers,
    "com.typesafe.akka" % "akka-remote" % "2.0.3",
    "com.typesafe.akka" % "akka-actor" % "2.0.3",
    "com.typesafe.akka" % "akka-kernel" % "2.0.3",
    "com.typesafe" %% "play-mini" % "2.0.3",
    "org.scalatest" %% "scalatest" % "1.7.1" % "test",
    "com.ning" % "async-http-client" % "1.7.1"
  )
}


