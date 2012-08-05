
name := "http-bridge"

version := ".0.2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ~= { seq =>
  val dispatchVers = "0.8.8"
  seq ++ Seq(
    "net.databinder" %% "dispatch-core" % dispatchVers,
    "net.databinder" %% "dispatch-http" % dispatchVers,
    "net.databinder" %% "dispatch-nio" % dispatchVers,
    "com.typesafe.akka" % "akka-remote" % "2.0.2",
    "com.typesafe.akka" % "akka-actor" % "2.0.2",
    "com.typesafe.akka" % "akka-kernel" % "2.0.2",
    "com.typesafe" %% "play-mini" % "2.0.1",
    "org.scalatest" %% "scalatest" % "1.7.1" % "test",
    "com.ning" % "async-http-client" % "1.7.1"
  )
}

