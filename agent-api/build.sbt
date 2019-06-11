import com.typesafe.sbt.packager.docker._

organization  := "edu.indiana.d2i.htrc"

name := "agent"

version       := "4.0.0-SNAPSHOT"

scalaVersion  := "2.12.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-language:postfixOps", "-encoding", "utf8")

resolvers ++= Seq(
  "typesafe repo"      at "http://repo.typesafe.com/typesafe/releases/",
  "spray repo"         at "http://repo.spray.io/",
  "sbt plugin repo" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases",
  "WSO2 Nexus" at "http://maven.wso2.org/nexus/content/groups/wso2-public/",
  "htrc nexus" at "http://nexus.htrc.illinois.edu/content/groups/public",
  "storehaus repo" at "http://repo1.maven.org/maven2/com/twitter/"
)

// mainClass in Compile := Some("HtrcJobsServer")

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

// removing existing docker cmds 
// dockerCommands := dockerCommands.value.filterNot {
//   case ExecCmd("ENTRYPOINT", args @ _*) => true
//   case ExecCmd("CMD", args @ _*) => true
//
//   // don't filter the rest; don't filter out anything that doesn't match a
//   // pattern
//   case cmd                       => false
// }

// the docker image gets a default name such as "agent:4.0.0-SNAPSHOT"; set
// the name to "agent:dev", "agent:prod" as needed
version in Docker := "dev"

dockerRepository := Some("docker-registry.htrc.indiana.edu")
dockerCommands ++= Seq(
  Cmd("USER", "root"),
  Cmd("ENV", "TZ=America/Indianapolis"),
  Cmd("RUN",
    "ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone"),
  Cmd("RUN", "groupadd htrcprodgrp"),
  Cmd("RUN", "chmod +x bin/agent"),
  Cmd("RUN", "useradd -M -s /bin/nologin -g htrcprodgrp -u 1488170 htrcprod"),
  // Cmd("RUN", "useradd -M -s /bin/nologin -g htrcprodgrp -u 500809 leunnikr"),
  // allow the user in the container to ssh to karst, and scp to and from karst
  Cmd("RUN", """echo "Host karst.uits.iu.edu\n\tIdentityFile /etc/htrc/agent/config/id_rsa\n\tStrictHostKeyChecking no\n" >> /etc/ssh/ssh_config"""),
  Cmd("RUN", "mkdir -p /etc/htrc/agent"),
  Cmd("RUN", "chown -R htrcprod /etc/htrc/agent"),
  Cmd("USER", "htrcprod")
  // launch the app using /opt/docker/bin/agent
  // Cmd("CMD", "bin/agent > /opt/docker/logs/agent-start-error.out")
)

libraryDependencies ++= {
  val akkaV       = "2.5.19"
  Seq(
  "org.scala-stm"           %%  "scala-stm" % "0.8",
  "com.typesafe.akka"       %%  "akka-actor"    % akkaV,
  "com.typesafe.akka"       %%  "akka-http"     % "10.1.7",
  "com.typesafe.akka"       %%  "akka-stream"   % akkaV,
  "com.typesafe.akka"       %%  "akka-http-xml" % "10.1.7",
  "com.typesafe.akka"       %%  "akka-slf4j"    % akkaV,
  "org.scala-lang.modules" %% "scala-xml" % "1.1.1",
  "ch.qos.logback" % "logback-classic" % "1.0.9",
  "org.apache.httpcomponents" % "httpcore" % "4.4.1",
  "org.apache.httpcomponents" % "httpclient" % "4.5.3",
  "com.twitter" % "storehaus-cache_2.10" % "0.10.0",
  "commons-io" % "commons-io" % "2.4",
  "com.github.tototoshi" %% "scala-csv" % "1.3.4",
  "com.auth0" % "java-jwt" % "3.3.0"
  )
}

// scala-xml requires sbt version 1.1.2, or later; the following handles
// earlier versions of sbt; it runs the code in a forked process in sbt 1.1.1
// and earlier, so that it doesn't conflict with the scala-xml on which the
// Scala compiler depends
fork := true
