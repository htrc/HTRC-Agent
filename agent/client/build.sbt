
name := "htrc-rest-client"

version := ".0.1"

scalaVersion := "2.9.1"

libraryDependencies ~= { seq =>
  val vers = "0.8.8"
  seq ++ Seq(
    "net.databinder" %% "dispatch-core" % vers,
    "net.databinder" %% "dispatch-http" % vers,
    "net.databinder" %% "dispatch-nio" % vers,
    "org.scalatest" %% "scalatest" % "1.7.1" % "test"
  )
}


        