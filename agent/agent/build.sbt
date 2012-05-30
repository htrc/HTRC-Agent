
name := "htrc-agent"

version := ".0.4"

scalaVersion := "2.9.1"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ~= { seq =>
  val dispatchVers = "0.8.8"
  seq ++ Seq(
    "net.databinder" %% "dispatch-core" % dispatchVers,
    "net.databinder" %% "dispatch-http" % dispatchVers,
    "net.databinder" %% "dispatch-nio" % dispatchVers,
    "com.typesafe.akka" % "akka-remote" % "2.0.1",
    "com.typesafe.akka" % "akka-actor" % "2.0.1",
    "com.typesafe.akka" % "akka-kernel" % "2.0.1",
    "com.typesafe" %% "play-mini" % "2.0",
    "org.scalatest" %% "scalatest" % "1.7.1" % "test",
    "com.ning" % "async-http-client" % "1.7.1"
  )
}
